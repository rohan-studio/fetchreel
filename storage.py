"""
Optional S3-compatible object storage for Fetchreel.

If S3_BUCKET is set (along with credentials), finished downloads are
uploaded there and served via a short-lived signed link instead of being
streamed from this server's own disk. Works with AWS S3 and any
S3-compatible provider — Cloudflare R2, Backblaze B2, DigitalOcean
Spaces, MinIO — by pointing S3_ENDPOINT_URL at that provider.

If S3_BUCKET isn't set, none of this runs: Fetchreel just serves files
from local temp storage as normal, and nothing else changes.

Environment variables:
    S3_BUCKET               required to turn this on
    S3_ACCESS_KEY_ID        required
    S3_SECRET_ACCESS_KEY    required
    S3_ENDPOINT_URL         optional — leave unset for real AWS S3
    S3_REGION               optional, default "auto" (fine for R2; for
                             real AWS S3, set this to your bucket's
                             actual region, e.g. "us-east-1")
    S3_URL_EXPIRES_SECONDS  optional, default 3600 (1 hour)

One-time setup on whichever bucket you use: add a lifecycle rule that
deletes objects after a day or so. This app doesn't delete uploaded
files itself — the signed link needs the object to still exist — so a
lifecycle rule is what keeps the bucket from filling up over time.
"""

import os
import uuid

_BUCKET = os.environ.get("S3_BUCKET")
_EXPIRES = int(os.environ.get("S3_URL_EXPIRES_SECONDS", "3600"))

_client = None
if _BUCKET:
    import boto3
    from botocore.client import Config

    _client = boto3.client(
        "s3",
        endpoint_url=os.environ.get("S3_ENDPOINT_URL") or None,
        aws_access_key_id=os.environ.get("S3_ACCESS_KEY_ID"),
        aws_secret_access_key=os.environ.get("S3_SECRET_ACCESS_KEY"),
        region_name=os.environ.get("S3_REGION", "auto"),
        config=Config(signature_version="s3v4"),
    )


def enabled() -> bool:
    return _client is not None


def upload_and_get_url(local_path: str, download_name: str) -> str:
    """Upload a finished download and return a signed, time-limited URL
    for it. Only call this when enabled() is True."""
    key = f"{uuid.uuid4().hex}-{download_name}"
    _client.upload_file(local_path, _BUCKET, key)
    return _client.generate_presigned_url(
        "get_object",
        Params={
            "Bucket": _BUCKET,
            "Key": key,
            "ResponseContentDisposition": f'attachment; filename="{download_name}"',
        },
        ExpiresIn=_EXPIRES,
    )
