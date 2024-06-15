FROM ubuntu:latest
LABEL authors="Jumpy"

ENTRYPOINT ["top", "-b"]
FROM OpenJDK:17-jre-alpine
# Define environment variables
ENV SPRING_OUTPUT_ANSI_ENABLE=ALWAYS  JAVA_OPTS=""
WORKDIR /GestionDeStock
ADD target/*.jar GestionDeStock.jar
EXPOSE 9092
CMD ["java","-jar","/GestionDeStock/GestionDeStock.jar"]