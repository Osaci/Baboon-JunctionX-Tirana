from fuzzywuzzy import fuzz, process
import pandas as pd
import boto3
from botocore.exceptions import ClientError

from PIL import Image
from io import BytesIO
import requests
import os
import json
import uuid
import re
import logging
import base64
from typing import Optional, List, Dict, Any, Optional, Tuple, Union

import vertexai
from vertexai.preview.generative_models import GenerativeModel, Part, SafetySetting, FinishReason, Tool, GenerationConfig
from vertexai.preview.generative_models import grounding
from vertexai.preview.generative_models import Image as VertexImage

from utils import exponential_backoff_retry, SDKRotator, S3ImageManager

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

logger = logging.getLogger(__name__)

# Read GCP service account keys from Docker secrets
gcp_key_paths = [
    os.environ.get('GCP_KEY_PATH_1', '/run/secrets/carbon_beanbag_key'),
    os.environ.get('GCP_KEY_PATH_2', '/run/secrets/spiritual_slate_key'),
    os.environ.get('GCP_KEY_PATH_3', '/run/secrets/ultra_function_key'),
]

# Load SDK configurations

SDK_CONFIGS = [
    {
        "project_id": "carbon-beanbag-410610",
        "key_path": gcp_key_paths[0],
        "location": "us-central1",
    },
    {
        "project_id": "spiritual-slate-410612",
        "key_path": gcp_key_paths[1],
        "location": "us-central1",
    },
    {
        "project_id": "ultra-function-439306-r4",
        "key_path": gcp_key_paths[2],
        "location": "us-central1",
    }
]

# Setup S3 manager for handling images
S3_BUCKET_NAME = os.environ.get("S3_BUCKET_NAME", "lilotest-images")
S3_REGION = os.environ.get("S3_REGION", "eu-north-1")
s3_manager = S3ImageManager(S3_BUCKET_NAME, S3_REGION)

# Setup SDK rotator
sdk_rotator = SDKRotator(SDK_CONFIGS)

# Common generation config
GENERATION_CONFIG = {
    "max_output_tokens": 8192,
    "temperature": 0.9,
    "top_p": 0.9,
}

# Safety settings
SAFETY_SETTINGS = [
    SafetySetting(
        category=SafetySetting.HarmCategory.HARM_CATEGORY_HATE_SPEECH,
        threshold=SafetySetting.HarmBlockThreshold.BLOCK_NONE
    ),
    SafetySetting(
        category=SafetySetting.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
        threshold=SafetySetting.HarmBlockThreshold.BLOCK_NONE
    ), 
    SafetySetting(
        category=SafetySetting.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
        threshold=SafetySetting.HarmBlockThreshold.BLOCK_NONE
    ), 
    SafetySetting(
        category=SafetySetting.HarmCategory.HARM_CATEGORY_HARASSMENT,
        threshold=SafetySetting.HarmBlockThreshold.BLOCK_NONE
    ), 
] 

# Search tool for gemini models
SEARCH_TOOL = [
    Tool.from_google_search_retrieval(
        google_search_retrieval=grounding.GoogleSearchRetrieval()
    ),
]

class BaboonQAManager:
    def __init__(self, bucket_url='https://questions-answers-baboon.s3.eu-north-1.amazonaws.com/questions_and_answers.xlsx'):
        self.bucket_url = bucket_url
        self.qa_data = None
        self.support_info = {
            "phone": "+355676038187",
            "email": "support@baboon.al"
        }
        self.load_qa_data()
    
    def load_qa_data(self):
        """Load Q&A data from S3 bucket via URL"""
        try:
            logger.info(f"Loading Q&A data from: {self.bucket_url}")
            
            # Test connection first
            try:
                import socket
                socket.create_connection(("questions-answers-baboon.s3.eu-north-1.amazonaws.com", 443), timeout=5)
                logger.info("Successfully connected to S3 host")
            except Exception as e:
                logger.error(f"Cannot connect to S3 host: {e}")
            
            # Try with different SSL settings
            try:
                # First attempt - normal request
                response = requests.get(self.bucket_url, timeout=30)
                logger.info(f"Normal request status: {response.status_code}")
            except Exception as e:
                logger.error(f"Normal request failed: {e}")
                
                # Second attempt - without SSL verification (for debugging only)
                try:
                    response = requests.get(self.bucket_url, timeout=30, verify=False)
                    logger.warning("Request succeeded without SSL verification")
                    logger.info(f"No-SSL request status: {response.status_code}")
                except Exception as e2:
                    logger.error(f"No-SSL request also failed: {e2}")
                    
                    # Third attempt - with explicit headers
                    try:
                        headers = {
                            'User-Agent': 'Mozilla/5.0',
                            'Accept': '*/*'
                        }
                        response = requests.get(self.bucket_url, headers=headers, timeout=30)
                        logger.info(f"Headers request status: {response.status_code}")
                    except Exception as e3:
                        logger.error(f"Headers request failed: {e3}")
                        raise e3
            
            response.raise_for_status()
            
            # Log successful response details
            logger.info(f"Response headers: {dict(response.headers)}")
            logger.info(f"Content length: {len(response.content)} bytes")
            
            # Load into pandas
            self.qa_data = pd.read_excel(BytesIO(response.content))
            logger.info(f"Successfully loaded {len(self.qa_data)} Q&A pairs from URL")
            logger.info(f"Columns in Q&A data: {self.qa_data.columns.tolist()}")
            
        except Exception as e:
            logger.error(f"Error loading Q&A data from URL: {e}")
            logger.exception("Full traceback:")
            self.qa_data = None
    
    def find_best_match(self, user_message):
        """Find best matching question using fuzzy matching"""
        if self.qa_data is None or self.qa_data.empty:
            logger.warning("No Q&A data available for matching")
            return None, 0
        
        questions = self.qa_data['Question'].tolist()
        logger.info(f"Searching for match among {len(questions)} questions")
        best_match, score = process.extractOne(user_message, questions, scorer=fuzz.token_sort_ratio)
        logger.info(f"Best match: '{best_match}' with score: {score}")
        
        # Get the corresponding answer
        answer_index = self.qa_data[self.qa_data['Question'] == best_match].index[0]
        answer = self.qa_data.loc[answer_index, 'Answer']
        
        return answer, score
    
    def process_question(self, user_message):
        """Process user question and return appropriate response"""
        answer, score = self.find_best_match(user_message)
        
        # Define thresholds
        GOOD_MATCH_THRESHOLD = 85
        POOR_MATCH_THRESHOLD = 40
      
        logger.info(f"Match score: {score}, Good threshold: {GOOD_MATCH_THRESHOLD}, Poor threshold: {POOR_MATCH_THRESHOLD}")
        
        if score >= GOOD_MATCH_THRESHOLD:
            # Good match - return the answer
            logger.info(f"Good match found, returning answer: {answer}")
            return {
                "type": "qa_answer",
                "response": answer,
                "confidence": "high"
            }
        elif score >= POOR_MATCH_THRESHOLD:
            # Poor match - return support contact info
            logger.info("Poor match, returning support contact")
            return {
                "type": "support_contact",
                "response": f"I'm not quite sure about that. You can contact our support team at {self.support_info['phone']} or email {self.support_info['email']}. Would you like to speak with a representative?",
                "confidence": "low",
                "support_info": self.support_info
            }
        else:
            # No match - return None to use regular bot response
            logger.info("No match found, returning None")
            return None

# Initialize the Q&A manager globally
qa_manager = BaboonQAManager()

def initialize_vertex_with_config(config: Dict[str, Any]) -> None:
    """Initialize vertex ai with the given configuration"""

    # Set google credentials for authentication
    if os.path.exists(config["key_path"]):
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = config["key_path"]

        # Initialize Vertex AI
        vertexai.init(
            project=config["project_id"],
            location=config["location"],
        )
        logger.info(f"Initialized Vertex AI with project {config['project_id']}")

    else:
        logger.error(f"GCP key file not found at: {config['key_path']}")
        raise FileNotFoundError(f"GCP key file not found: {config['key_path']}")

def is_image_request(message: str) -> bool:
    """Check if the message is requesting an image generation"""
    # Check for image generation keywords (.image or image:)
    pattern = r"(\.image|image:)"
    return bool(re.search(pattern, message, re.IGNORECASE))

def generate_text_response(message: str, history: List[Dict[str, Any]]) -> str:
    """Generate text response using Gemini model with exponential retry and SDK rotation logic.
    """
    def _generate_with_current_sdk():
        config = sdk_rotator.get_current_config()
        initialize_vertex_with_config(config)

        instruction = """Helpful and assisting ai."""

        model = GenerativeModel(
            "gemini-1.5-flash-002",
            system_instruction=[instruction],
            #tools=SEARCH_TOOL,
            generation_config=GENERATION_CONFIG,
            safety_settings=SAFETY_SETTINGS,
        )
            
        # Format history into Vertex AI format
        formatted_history = []
        for entry in history:
            formatted_history.append({"role": "user", "parts": [{"text": entry["user_message"]}]})

            if "bot_message" in entry:        
                formatted_history.append({"role": "model", "parts": [{"text": entry["bot_message"]}]})

        # Add current message
        conversation = formatted_history + [{"role": "user", "parts": [{"text": message}]}]

        # Generate response
        response = model.generate_content(conversation)

        if hasattr(response, 'text'):
            return response.text
        elif hasattr(response, 'parts'):

            text_parts = []
            for part in response.parts:
                if hasattr(part, 'text') and part.text:
                    text_parts.append(part.text)
            return " ".join(text_parts)
        else:
            return str(response)

    try:
        # Try to generate with exponential backoff and SDK rotation
        return exponential_backoff_retry(_generate_with_current_sdk)
    except Exception as e:
        # If all SDKs fail after retries, return an error message
        logger.error(f"All SDKs failed to generate text response: {str(e)}")
        return f"I'm sorry, I'm having trouble processing your request right now. Please try again later. (Error: {str(e)})"


def compress_to_webp(image_data: bytes, quality: int = 85, max_size: int = 800) -> Tuple[bytes, str]:
    """
    Compress image to WebP format with specified quality

    Args:
        image_data: Raw image bytes
        quality: WebP compression quality (0-100%)
        max_size: Maximum dimension for resizing large images

    Returns:
        Tuple of (compressed image bytes, mimetype)
    """

    # Open image from bytes
    img = Image.open(BytesIO(image_data))

    # Resize if the image is too large while maintaining aspect ratio
    if max(img.width, img.height) > max_size:
        if img.width > img.height:
            new_width = max_size
            new_height = int(img.height * (max_size / img.width))
        else:
            new_height = max_size
            new_width = int(img.width * (max_size / img.height))

        img = img.resize((new_width, new_height), Image.LANCZOS)

    # Save as WebP with specified quality
    output = BytesIO()
    img.save(output, format="WEBP", quality=quality)
    output.seek(0)

    return output.getvalue(), "image/webp"


def generate_image(prompt: str) -> Tuple[str, Optional[str]]:
    """
    Generate image using Imagen 3 with retry and rotation logic,
    compress to WebP and handle fallbacks

    Args:
        prompt: Text prompt for image generation

    Returns:
        Tuple of (S3 URL or None, base64 data URL or None)
    """

    def _generate_with_current_sdk():

        config = sdk_rotator.get_current_config()
        initialize_vertex_with_config(config)

        # Clean up the prompt - remove image keywords
        clean_prompt = re.sub(r"(\.image|image:)", "", prompt, flags=re.IGNORECASE).strip()

        from vertexai.preview.vision_models import ImageGenerationModel

        generation_model = ImageGenerationModel.from_pretrained("imagen-3.0-generate-002")

        image_response = generation_model.generate_images(
            prompt=clean_prompt,
            number_of_images=1,
            aspect_ratio="1:1",
        )   

        logger.info(f"image_response: {image_response}")
        # Extract image data - first try the new API format
        try:
            if not image_response:
                raise ValueError("No images were created")

            image_data = image_response[0]._image_bytes

            # Compress to WebP format and 85% quality
            compressed_data, mimetype = compress_to_webp(image_data, quality=85)

            # Generate unique filename with WebP extension
            filename = f"image_{uuid.uuid4()}.webp"

            try:
                # Try to upload to S3
                image_url = s3_manager.upload_image(compressed_data, filename, content_type=mimetype)

                if hasattr(image_response[0], 'enhanced_prompt'):
                    logger.info(f"Enhanced prompt: {image_response[0].enhanced_prompt}")

                # Success - return URL with no fallback needed
                return image_url, None
            except Exception as s3_error:
                # S3 upload failed, use base64 fallback with further compression
                logger.warning(f"Failed to upload to S3, using base64 fallback: {str(s3_error)}")

                # Apply more aggressive compression for fallback (70% quality, 600px max)
                fallback_data, fallback_mimetype = compress_to_webp(image_data, quality=70, max_size=600)

                # Convert to base64 data URL
                base64_encoded = base64.b64encode(fallback_data).decode('utf-8')
                data_url = f"data:{fallback_mimetype};base64,{base64_encoded}"
 
                return None, data_url

        except Exception as processing_error:
            logger.error(f"Error processing image: {str(processing_error)}")
            raise

    try:
        # Try to generate with exponential backoff and SDK rotation
        return exponential_backoff_retry(_generate_with_current_sdk)
    except Exception as e:
        # If all SDKs fail after some retries, return an error message
        logger.error(f"All SDKs failed to generate image: {str(e)}")
        return None, None


def enhance_s3_image_manager():
    """Add a method for WebP compression to the S3ImageManager class"""
    
    def upload_webp_image(self, image_data: bytes, key_name: str, quality: int = 85, max_size: int = 800) -> str:
        """
        Compress image to WebP and upload to S3
        
        Args:
            image_data: Original image data
            key_name: S3 object key name
            quality: WebP compression quality (0-100)
            max_size: Maximum dimension for resizing
     
        Returns:
            Public URL of the uploaded image
        """
        compressed_data, mimetype = compress_to_webp(image_data, quality, max_size)

        # Use existing upload_image method with the compressed data
        return self.upload_image(compressed_data, key_name, content_type=mimetype)

    # Add the method to the S3 Image Manager class 
    S3ImageManager.upload_webp_image = upload_webp_image

def extract_current_message(full_prompt: str) -> str:
    """Extract the current user message from the full prompt with history"""
    
    # Look for the CURRENT USER MESSAGE marker
    marker = "=== CURRENT USER MESSAGE ==="
    
    if marker in full_prompt:
        # Split at the marker and take everything after it
        parts = full_prompt.split(marker)
        if len(parts) > 1:
            current_message = parts[1].strip()
            logger.info(f"Extracted current message: {current_message}")
            return current_message
    
    # If no marker found, return the full prompt
    logger.info("No marker found, using full prompt")
    return full_prompt.strip()

def process_message(message: str, history: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Process incoming message and generate appropriate response"""
    
    # Extract the current message for Q&A matching
    current_message = extract_current_message(message)
    
    # First, check if this is a Q&A type question
    qa_result = qa_manager.process_question(current_message)
    
    if qa_result:
        # Q&A system has a response
        if qa_result["type"] == "qa_answer":
            response = {
                "type": "text",
                "text": qa_result["response"],
                "source": "qa_system"
            }
        elif qa_result["type"] == "support_contact":
            response = {
                "type": "support_contact",
                "text": qa_result["response"],
                "support_info": qa_result["support_info"],
                "show_representative_button": True
            }
        
        history_entry = {
            "user_message": current_message,  # Store only the current message
            "bot_message": qa_result["response"]
        }
        
        return {"response": response, "history_entry": history_entry}
    
    # If no Q&A match, proceed with regular processing
    if is_image_request(current_message):
        # ... (existing image generation code)
        logger.info(f"Processing image generation request: {current_message}")
        image_url, image_base64 = generate_image(current_message)
        
        text_response = "Generated image"
        if image_url:
            text_response = f"{text_response}\n!IMAGEURL!{image_url}"
        elif image_base64:
            text_response = f"{text_response}\n!IMAGEDATA!{image_base64}"
        
        response = {
            "type": "image",
            "text": text_response,
            "url": image_url,
            "base64": image_base64
        }
        
        history_entry = {
            "user_message": current_message,  # Store only the current message
            "bot_message": "image"
        }
    else:
        # Generate text response using the full prompt
        logger.info(f"Processing text request: {message}")  # Use full message for context
        text_response = generate_text_response(message, history)  # Pass full message
        
        response = {
            "type": "text",
            "text": text_response
        }
        
        history_entry = {
            "user_message": current_message,  # Store only the current message
            "bot_message": text_response
        }
    
    return {"response": response, "history_entry": history_entry}
    
    # If no Q&A match, proceed with regular processing
    if is_image_request(message):
        # ... (existing image generation code)
        logger.info(f"Processing image generation request: {message}")
        image_url, image_base64 = generate_image(message)
        
        text_response = "Generated image"
        if image_url:
            text_response = f"{text_response}\n!IMAGEURL!{image_url}"
        elif image_base64:
            text_response = f"{text_response}\n!IMAGEDATA!{image_base64}"
        
        response = {
            "type": "image",
            "text": text_response,
            "url": image_url,
            "base64": image_base64
        }
        
        history_entry = {
            "user_message": message,
            "bot_message": "image"
        }
    else:
        # Generate text response
        logger.info(f"Processing text request: {message}")
        text_response = generate_text_response(message, history)
        
        response = {
            "type": "text",
            "text": text_response
        }
        
        history_entry = {
            "user_message": message,
            "bot_message": text_response
        }
    
    return {"response": response, "history_entry": history_entry}
    
    logger.info("No QA match, proceeding with regular processing")
    
    # If no Q&A match, proceed with regular processing
    if is_image_request(message):
        # ... (existing image generation code)
        logger.info(f"Processing image generation request: {message}")
        image_url, image_base64 = generate_image(message)
        
        text_response = "Generated image"
        if image_url:
            text_response = f"{text_response}\n!IMAGEURL!{image_url}"
        elif image_base64:
            text_response = f"{text_response}\n!IMAGEDATA!{image_base64}"
        
        response = {
            "type": "image",
            "text": text_response,
            "url": image_url,
            "base64": image_base64
        }
        
        history_entry = {
            "user_message": message,
            "bot_message": "image"
        }
    else:
        # Generate text response
        logger.info(f"Processing text request: {message}")
        text_response = generate_text_response(message, history)
        
        response = {
            "type": "text",
            "text": text_response
        }
        
        history_entry = {
            "user_message": message,
            "bot_message": text_response
        }
    
    return {"response": response, "history_entry": history_entry}    
    # If no Q&A match, proceed with regular processing
    if is_image_request(message):
        # ... (existing image generation code)
        logger.info(f"Processing image generation request: {message}")
        image_url, image_base64 = generate_image(message)
        
        text_response = "Generated image"
        if image_url:
            text_response = f"{text_response}\n!IMAGEURL!{image_url}"
        elif image_base64:
            text_response = f"{text_response}\n!IMAGEDATA!{image_base64}"
        
        response = {
            "type": "image",
            "text": text_response,
            "url": image_url,
            "base64": image_base64
        }
        
        history_entry = {
            "user_message": message,
            "bot_message": "image"
        }
    else:
        # Generate text response
        logger.info(f"Processing text request: {message}")
        text_response = generate_text_response(message, history)
        
        response = {
            "type": "text",
            "text": text_response
        }
        
        history_entry = {
            "user_message": message,
            "bot_message": text_response
        }
    
    return {"response": response, "history_entry": history_entry}