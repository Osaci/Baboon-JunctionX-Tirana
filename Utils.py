import time
import logging
import random
import os
import boto3
import base64
from typing import Callable, List, Dict, Any, Optional
from botocore.exceptions import ClientError

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

logger = logging.getLogger(__name__)



class SDKRotator:
    def __init__(self, sdk_configs: List[Dict[str, Any]]):
        self.sdk_configs = sdk_configs
        self.current_index = 0

    def get_current_config(self) -> Dict[str, Any]:
        return self.sdk_configs[self.current_index]
    
    def rotate(self) -> Dict[str, Any]:
        self.current_index = (self.current_index + 1) % len(self.sdk_configs)
        return self.get_current_config()

def exponential_backoff_retry(

    func: Callable,
    max_retries: int = 3,
    base_delay: float = 1.0,
    max_delay: float = 60.0,
    jitter: bool = True
) -> Any:

    """
    Execute a function with exponential backoff retry logic

    Args:
        func: The function to execute
        max_retries: Maximum number of retry attempts
        base_delay: Initial delay between retries in seconds
        max_delays: Maximum delay between retries in seconds
        jitter: Whether to add random jitter into the delay

    Returns:
        Returns result if function call is successful

    Raises:
        Exception: The last exception encountered if all retries fail
    """

    last_exception = None

    for attempt in range(max_retries):
        try:
            return func()
        except Exception as e:
            last_exception = e
            if attempt == max_retries - 1:
                logger.error(f"All {max_retries} retry attempts failed")
                raise

            delay = min(base_delay * (2 ** attempt), max_delay)

            if jitter:
                delay = delay * (0.5 + random.random())

            logger.warning(f"Attempt {attempt + 1}/{max_retries} failed: {str(e)}. Retrying in {delay:.2f}s")
            time.sleep(delay)

    # The above exception should be raised instead of this one
    raise last_exception



def read_secret(secret_path):
    try:
        with open(secret_path, 'r') as file:
            return file.read().strip()
    except Exception as e:
        logger.error(f"Failed to read secret from {secret_path}: {e}")
        return None

aws_access_key_path = os.environ.get('AWS_ACCESS_KEY_FILE', '/run/secrets/aws_access_key')
aws_secret_key_path = os.environ.get('AWS_SECRET_KEY_FILE', '/run/secrets/aws_secret_key')

# Read secrets and set them as environment variables for boto3
if os.path.exists(aws_access_key_path) and os.path.exists(aws_secret_key_path):
    aws_access_key = read_secret(aws_access_key_path)
    aws_secret_key = read_secret(aws_secret_key_path)

    # Set as environment variables for boto3 to use automatically
    os.environ['AWS_ACCESS_KEY_ID'] = aws_access_key
    os.environ['AWS_SECRET_ACCESS_KEY'] = aws_secret_key
    logger.info("AWS Credentials loaded from secrets")
else:
    logger.warning("AWS credential secrets not found or not accessible")

S3_BUCKET_NAME = os.environ.get("S3_BUCKET_NAME", "lilotest-images")
S3_REGION = os.environ.get("S3_REGION", "eu-north-1")


class S3ImageManager:
    def __init__(self, bucket_name: str, region: str = 'eu-north-1'):
        self.bucket_name = bucket_name
        self.region = region
        self.s3_client = boto3.client('s3', region_name=region)

    def upload_image(self, image_data: bytes, key_name: str, content_type: str = 'image/png') -> str:

        """
        Upload an image to S3 and return its URL

        Args:
            image_data: Binary image data
            key_name: S3 object key name (path in bucket)
            content_type: MIME type of the image

        Returns:
            Public URL of the uploaded image
        """

        try:
            self.s3_client.put_object(
                Bucket=self.bucket_name,
                Key=key_name,
                Body=image_data,
                ContentType=content_type, 
            )

            # Construct the URL using the bucket and region
            url = f"https://{self.bucket_name}.s3.{self.region}.amazonaws.com/{key_name}"
            return url

        except ClientError as e:
            logger.error(f"Failed to upload image to S3: {e}")
            raise

    def get_image_as_base64(self, key_name: str) -> str:
        """
        Get an image from S3 and return as base64-encoded string (fallback method)

        Args:
            key_name: S3 object key name

        Returns:
            Base64-encoded string representation of the image
        """
        try:
            response = self.s3_client.get_object(
                Bucket=self.bucket_name,
                Key=key_name
            )

            image_data = response['Body'].read()
            base64_encoded = base64.b64encode(image_data).decode('utf-8')

            return base64_encoded

        except ClientError as e:
            logger.error(f"Failed to get image from S3: {e}")
            raise