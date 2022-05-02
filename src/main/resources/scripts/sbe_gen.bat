SET ORIGINAL=%CD%
cd ..\..\..\..
java -Dsbe.output.dir=target/generated-sources/java -jar lib/sbe-all-1.4.0-RC4.jar src/main/resources/sbe/message-schema.xml
java -Dsbe.output.dir=target/generated-sources/java -jar lib/sbe-all-1.4.0-RC4.jar src/main/resources/sbe/journal-schema.xml
cd %ORIGINAL%
