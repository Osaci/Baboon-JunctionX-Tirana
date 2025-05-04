import os
import json
import uuid
import redis
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, Request, Response, Cookie, Depends, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from starlette.responses import JSONResponse

from vertex import process_message

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

logger = logging.getLogger(__name__)

app = FastAPI(title="Lilotest Chat API")

origins = [
    "https://lilotest.com",
    "http://localhost:3000",
    "http://localhost:8000",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def read_secret(secret_path):
    try:
        with open(secret_path, 'r') as file:
            return file.read().strip()
    except Exception as e:
        logger.error(f"Failed to read secret from {secret_path}: {e}")
        return None

redis_password_path = os.environ.get('REDIS_PASSWORD_FILE', '/run/secrets/redis_password')

# Read secrets and set them as environment variables for boto3

REDIS_PASSWORD = None
if os.path.exists(redis_password_path):
    REDIS_PASSWORD = read_secret(redis_password_path)
    if REDIS_PASSWORD:
        print(f"Redis password loaded succesfully, length: {len(REDIS_PASSWORD)}") 
    else:
        print(f"Failed to load Redis password") 
else:
    print(f"Redis password not found at path: {redis_password_path}") 

# Initialize Redis client
REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", 6379))
REDIS_DB = int(os.environ.get("REDIS_DB", 0))
#REDIS_PASSWORD = os.environ.get("REDIS_PASSWORD", None)

redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT, 
    db=REDIS_DB,
    password=REDIS_PASSWORD,
    decode_responses=True
)

try:
    redis_client.ping()
    print("Redis connection successful")
except Exception as e:
    print(f"Redis connection failed: {e}")

print(f"Secret path exists: {os.path.exists(redis_password_path)}")
print(f"REDIS_PASSWORD length: {len(REDIS_PASSWORD) if REDIS_PASSWORD else 0}")

# Session expiry time (24 hours)
SESSION_EXPIRY = 60 * 60 * 24

# Models 
class MessageRequest(BaseModel):
    message: str

class MessageResponse(BaseModel):
    response: Dict[str, Any]
    session_id: str

# Helper functions
def get_or_create_session(session_id: Optional[str] = None) -> str:
    """Get existing session or create a new one"""
    if session_id and redis_client.exists(f"session:{session_id}"):
        # Reset session expiry time
        redis_client.expire(f"session:{session_id}", SESSION_EXPIRY)
        redis_client.expire(f"history:{session_id}", SESSION_EXPIRY)
        return session_id

    # Create new session
    new_session_id = str(uuid.uuid4())
    redis_client.set(f"session:{new_session_id}", json.dumps({
        "created_at": datetime.now().isoformat(),
    }), ex=SESSION_EXPIRY)

    # Initialize empty history
    redis_client.set(f"history:{new_session_id}", json.dumps([]), ex=SESSION_EXPIRY)

    return new_session_id

def get_chat_history(session_id: str) -> List[Dict[str, Any]]:
    """Get chat history for a session"""
    history_json = redis_client.get(f"history:{session_id}")
    if history_json:
        return json.loads(history_json)
    return []

def update_chat_history(session_id: str, entry: Dict[str, Any]) -> None:
    """Update chat history for a session"""
    history = get_chat_history(session_id)
    history.append(entry)
    redis_client.set(f"history:{session_id}", json.dumps(history), ex=SESSION_EXPIRY)

@app.post("/send-message", response_model=MessageResponse)
async def send_message(
    request: MessageRequest,
    response: Response,
    session_id: Optional[str] = Cookie(None)
) -> MessageResponse:

    """
    Send message to the chat for response
    """
    # Get or create a session
    session_id = get_or_create_session(session_id)

    # Set secure cookie
    response.set_cookie(
        key="session_id",
        value=session_id,
        max_age=SESSION_EXPIRY,
        httponly=True,
        secure=True,
        samesite="lax"
    )

    # Get chat history 
    history = get_chat_history(session_id)

    # Process images
    result = process_message(request.message, history)

    # Update chat history
    update_chat_history(session_id, result["history_entry"])

    return MessageResponse(
        response=result["response"],
        session_id=session_id
    )

@app.get("/cleanup-sessions")
async def cleanup_expired_sessions():
    """Admin endpoint to clean up expired sessions"""
    # This should be protected with authentication 

    pattern = "session:*"
    cursor = 0
    cleaned = 0

    while True:
        cursor, keys = redis_client.scan(cursor, pattern, 100)
        for key in keys:
            session_id = key.split(":")[1]
            if not redis_client.exists(key):
                #Delete session history
                redis_client.delete(f"history:{session_id}")
                cleaned += 1

        if cursor == 0:
            break

    return {"status": "success", "cleaned_sessions": cleaned}

# Run with: uvicorn app:app --reload
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)