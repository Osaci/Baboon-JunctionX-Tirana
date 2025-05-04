FROM python:3.9-slim

WORKDIR /app

# Install dependencies
COPY ./app/requirements.txt /app
RUN pip install --no-cache-dir -r /app/requirements.txt

# Copy application code
COPY ./app/ /app/

# Create directories for secrets and keys
RUN mkdir -p /app/keys /app/secrets

# Expose part
EXPOSE 8000

# Command to run the application
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]