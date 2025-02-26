FROM openjdk:17.0.2-jdk-slim
RUN apt-get update && apt-get install -y fontconfig libfreetype6
RUN mkdir /app
COPY /target/job-runner-0.0.1-SNAPSHOT.jar /app/job-runner.jar
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "job-runner.jar"]
