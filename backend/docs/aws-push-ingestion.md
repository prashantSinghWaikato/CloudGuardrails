# AWS Push Ingestion

This repository now exposes an internal ingestion endpoint for AWS-originated events:

- `POST /internal/aws/events`
- Header: `X-Guardrails-Ingestion-Secret`
- Payload: EventBridge or CloudTrail-shaped JSON

The backend normalizes common CloudTrail fields and then sends them through Kafka for rule evaluation and remediation.

## Recommended AWS Flow

Use this shape in production:

1. Enable CloudTrail management events in the AWS account.
2. Let EventBridge receive `AWS API Call via CloudTrail` events.
3. Trigger a Lambda function for those events.
4. Have the Lambda function forward the raw event JSON to Guardrails.

For cost control, deploy in `minimal-cost` mode unless you explicitly want broader AWS API coverage.

This repository includes a minimal SAM package for that Lambda in:

- [infra/aws-forwarder/template.yaml](/Users/lio/Downloads/guardrails/infra/aws-forwarder/template.yaml)
- [infra/aws-forwarder/src/app.py](/Users/lio/Downloads/guardrails/infra/aws-forwarder/src/app.py)

## Deploy

From the repository root:

```bash
cd infra/aws-forwarder
sam build
sam deploy --guided
```

Provide these parameter values during deployment:

- `GuardrailsIngestionUrl`
  Example: `https://guardrails.example.com/internal/aws/events`
- `GuardrailsIngestionSecret`
  Must match `GUARDRAILS_INGESTION_SECRET` on the Guardrails backend
- `EventBusName`
  Usually `default`
- `CaptureMode`
  Use `minimal-cost` to forward only the AWS API events used by the current ruleset

## Backend Configuration

Set the Guardrails ingestion secret on the backend:

```bash
export GUARDRAILS_INGESTION_SECRET="replace-with-a-long-random-secret"
```

The backend reads this value from:

- [src/main/resources/application.yaml](/Users/lio/Downloads/guardrails/src/main/resources/application.yaml)

## Event Pattern

The included SAM template subscribes the forwarder Lambda to:

```yaml
detail-type:
  - AWS API Call via CloudTrail
```

The SAM template supports two capture modes:

- `minimal-cost`
  Only forwards the AWS API events used by the current Guardrails ruleset
- `broad-capture`
  Forwards all `AWS API Call via CloudTrail` events

`minimal-cost` is the default and is the recommended option if you want AWS cost near zero.

In `minimal-cost` mode, the EventBridge rule forwards only:

```yaml
detail:
  eventName:
    - AuthorizeSecurityGroupIngress
    - PutBucketAcl
    - PutBucketPolicy
    - PutBucketEncryption
    - CreateUser
    - CreateAccessKey
    - AttachUserPolicy
    - ConsoleLogin
    - RunInstances
```

That event list is intentionally aligned to the current rules in [rules.yml](/Users/lio/Downloads/guardrails/src/main/resources/rules.yml). If you add new rules later, update the EventBridge pattern too.

## Rule-Relevant Fields

The backend normalizer extracts common fields used by the current ruleset, including:

- `eventName`
- `eventID`
- AWS account ID
- `sourceIPAddress`
- security group ID, port, and CIDR
- bucket name and ACL-related fields
- IAM user and access key fields

The normalized event is then processed by:

- [src/main/java/com/cloud/guardrails/service/AwsPushIngestionService.java](/Users/lio/Downloads/guardrails/src/main/java/com/cloud/guardrails/service/AwsPushIngestionService.java)
- [src/main/java/com/cloud/guardrails/service/EventConsumer.java](/Users/lio/Downloads/guardrails/src/main/java/com/cloud/guardrails/service/EventConsumer.java)

## Operational Notes

- The ingestion endpoint is internal. Put it behind TLS and a private or tightly controlled network path.
- The shared secret is a baseline control, not a full trust model. A stronger next step is request signing or API Gateway auth.
- The forwarder retries automatically through Lambda/EventBridge retry behavior when Guardrails is temporarily unavailable.
- Duplicate CloudTrail events are deduplicated in the backend by `externalEventId` plus cloud account.

## Current Remediation Coverage

These actions now execute real AWS API calls:

- `REVOKE_SECURITY_GROUP_RULE`
- `BLOCK_PUBLIC_S3_ACCESS`
- `DISABLE_ACCESS_KEY`

These are still manual or simulated:

- `TAG_RESOURCE`
- any unsupported remediation action
