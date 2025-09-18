FROM maven:3.8-openjdk-17
WORKDIR /
COPY src ./src
COPY pom.xml ./pom.xml
RUN mvn package
EXPOSE 8080
CMD java -jar target/FraudAgent.jar