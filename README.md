# Job Runner Application

## Overview

This application is built using **Spring Boot** and is designed to handle long-running background tasks. Users can initiate a task, retrieve its ID, and later query the task's status and result. The system ensures that:

- Identical tasks are not started simultaneously.
- The application can recover from failures and resume tasks from the last completed step.
- The solution supports multiple instances of the application.

## Technologies Used

- **Spring Boot**
- **Kafka** for distributed task execution
- **Redis** for storing job statuses and ensuring fault tolerance
- **Spring Scheduler** for detecting and recovering lost jobs
- **Springdoc OpenAPI** for API documentation with Swagger UI
- **JUnit 5 & Testcontainers** for integration testing

## API Documentation

The API is documented using **Swagger UI**. After starting the application, you can access the documentation at:

```
http://localhost:8080/swagger-ui/index.html
```

## API Endpoints

### Create a Job

**Request:**

```http
POST /jobs
Content-Type: application/json
```

```json
{
  "min": 1,
  "max": 100,
  "count": 20
}
```

**Response:**

```json
{
  "jobId": "6faea0ae-6d1e-4da5-bb64-a7c0175edb88"
}
```

### Get Job Status

**Request:**

```http
GET /jobs/{id}
```

**Response:**

```json
{
  "status": "COMPLETED",
  "progress": "100",
  "result": "[12, 45, 78, ...]",
  "retries": 0
}
```

## Features

- **Unique Task Handling:** Prevents the same task from running simultaneously.
- **Fault Tolerance:** Jobs resume from the last checkpoint in case of a failure.
- **Scalability:** Multiple instances of the application can run concurrently.
- **Background Processing:** Uses Kafka and Redis to handle long-running tasks asynchronously.
- **API Documentation:** Swagger UI provides an interactive API documentation interface.

### **Common Step: Clone the repository**  
Before proceeding with any of the options below, clone the repository:

```bash
git clone https://github.com/Kvansipto/job-runner.git
cd job-runner
```

---

### **1. Run everything with Docker (infra + services)**  
In this setup, both the infrastructure and services are run in Docker containers using their respective profiles.

For this option just run the following command:
   ```bash
   docker-compose --profile infra --profile services up -d
   ```
---

### **2. Run infrastructure with Docker, services locally**  
Here, only the infrastructure runs in Docker, and the services are run locally using the `local` profile in your IDE.

**Steps:**  
**1. Start only the infrastructure using the `infra` profile:**
```bash
   docker-compose --profile infra up -d
   ```
**2. Run the services locally in your IDE:**
Use the `local` Spring profile when launching the services.

---

### **3. Run everything locally (without Docker)**  
In this setup, both infrastructure and services are run locally without Docker.

**Steps:**

**1. Install the local environment:**
Ensure that the services are pointing to the following infrastructure endpoints:

| Component    | URL                               |
|--------------|-----------------------------------|
| Redis        | `localhost:6379`                  |
| Kafka Brokers| `localhost:9092, localhost:9093, localhost:9094` |

**2. Run the services locally using the `local` Spring profile in your IDE.**

## Testing

The application includes unit and integration tests covering:

- Job creation and execution
- Duplicate job prevention
- Job recovery after failure
- Concurrent job execution handling

