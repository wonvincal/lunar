#!/bin/bash
cd ../../../../
rm -rf target/generated-sources/java
${JAVA_HOME?Please set JAVA_HOME}/bin/java -Dsbe.output.dir=target/generated-sources/java -jar lib/sbe-all-1.4.0-RC4.jar src/main/resources/sbe/message-schema.xml
${JAVA_HOME}/bin/java -Dsbe.output.dir=target/generated-sources/java -jar lib/sbe-all-1.4.0-RC4.jar src/main/resources/sbe/journal-schema.xml

rm -rf target/generated-sources/cpp
${JAVA_HOME?Please set JAVA_HOME}/bin/java -Dsbe.output.dir=target/generated-sources/cpp -Dsbe.target.language=cpp98 -jar lib/sbe-all-1.1.6-RC2-SNAPSHOT.jar src/main/resources/sbe/message-schema.xml
${JAVA_HOME}/bin/java -Dsbe.output.dir=target/generated-sources/cpp -Dsbe.target.language=cpp98 -jar lib/sbe-all-1.1.6-RC2-SNAPSHOT.jar src/main/resources/sbe/journal-schema.xml

