import json
import os
import urllib.error
import urllib.request


INGESTION_URL = os.environ["GUARDRAILS_INGESTION_URL"]
INGESTION_SECRET = os.environ["GUARDRAILS_INGESTION_SECRET"]


def handler(event, context):
    payload = json.dumps(event).encode("utf-8")

    request = urllib.request.Request(
        INGESTION_URL,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Guardrails-Ingestion-Secret": INGESTION_SECRET,
            "User-Agent": "guardrails-aws-forwarder/1.0",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            status_code = response.getcode()
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Guardrails ingestion failed with status {error.code}: {body}"
        ) from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Unable to reach Guardrails ingestion endpoint: {error}") from error

    if status_code < 200 or status_code >= 300:
        raise RuntimeError(
            f"Guardrails ingestion returned unexpected status {status_code}: {response_body}"
        )

    return {
        "statusCode": status_code,
        "body": response_body,
        "forwardedEventId": event.get("id"),
        "awsRequestId": context.aws_request_id,
    }
