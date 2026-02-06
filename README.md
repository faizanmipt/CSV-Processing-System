# CSV Processing System

A distributed microservices system for processing CSV files with email detection. Built with Spring Boot, Kafka, and WebFlux.

---

## Quick Start

```bash
# Start with Docker
docker-compose up -d

# Verify
docker-compose ps
```



## Services

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Single entry point |
| Upload | 8081 | File upload |
| Worker | 8082 | CSV processing |
| Download | 8083 | File download + status check |
| Job Store | 8084 | Centralized status tracking |
| Kafka | 9092 | Message broker |
| Zookeeper | 2181 | Kafka coordination |

---

## Usage

### 1. Upload a CSV File

```bash
curl -X POST http://localhost:8080/API/upload \
  -F "file=@yourfile.csv"

# Response: {"id": "uuid"}
```

### 2. Check Status

```bash
curl http://localhost:8080/status/{jobId}

# Status values: RECEIVED, STORED, QUEUED, IN_PROGRESS, COMPLETED, FAILED
```

### 3. Download Result

```bash
curl -O http://localhost:8080/API/download/{jobId}
```

---

## What It Does

**Input CSV:**
```csv
name,email,age
John Doe,john@example.com,30
Jane,,25
```

**Output CSV:**
```csv
name,email,age,has_email
John Doe,john@example.com,30,true
Jane,,25,false
```

---

## Data Flow

```
Upload Service:
  1. Receive file → 2. Save to /data/raw → 3. Update RECEIVED
  4. Update STORED → 5. Publish to Kafka → 6. Update QUEUED

Worker Service:
  1. Consume from Kafka → 2. Update IN_PROGRESS
  3. Process CSV (detect emails) → 4. Save to /data/processed
  5. Update COMPLETED (or FAILED with DLQ)

Download Service:
  1. Query Job Store → 2. Check status
  3. Stream file (if COMPLETED) → Return error otherwise
```

---

## Commands

```bash
# Build
mvn clean install

# Run tests
mvn test

# View logs
docker-compose logs -f

# Scale workers
docker-compose up -d --scale worker-service=3

# Health check
curl http://localhost:8080/actuator/health
```

---

## Configuration

Edit `config.yaml` for centralized settings:

```yaml
kafka:
  bootstrap_servers: "kafka:29092"
  topic: "csv-jobs"
  dlq_topic: "csv-jobs-dlq"

storage:
  raw_dir: "/data/raw"
  processed_dir: "/data/processed"
```

---

## Retry Policy

- Max attempts: 5
- Initial delay: 1s
- Multiplier: 2x
- Max delay: 10s
- Failed jobs → Dead Letter Queue (csv-jobs-dlq)

---

## Project Structure

```
csv-processing-system/
├── api-gateway/              # API Gateway
├── upload-service/           # File upload
├── worker-service/           # CSV processing
├── download-service/         # File download
├── common-jobstore-service/  # Status tracking
├── config.yaml              # Configuration
└── docker-compose.yml       # Docker setup
```

---



## Tech Stack

- Java 17 | Spring Boot 3.x | WebFlux
- Kafka | Maven | Docker

---
