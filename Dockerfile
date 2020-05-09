FROM gradle:5.4-jdk12 as compile
# must change this accordingly with whichever directory you're pulling from 
WORKDIR /home/gradle/src			 
ADD --chown=gradle:gradle . /home/gradle/src
# Default gradle user is 'gradle'. We need to add permission on working directory for gradle to build 
USER root


#RUN chown -R gradle /home/gradle/src
#USER gradle


RUN gradle build fatJar


FROM openjdk:12
WORKDIR /home/application/java

COPY --from=compile "/home/gradle/src/build/libs/cis-reports.jar" .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "cis-reports.jar"]