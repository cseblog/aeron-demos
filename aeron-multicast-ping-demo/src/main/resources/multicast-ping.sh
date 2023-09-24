java -jar \
    -Daeron.endpoint.ip="localhost" \
    -Daeron.endpoint.port="7775" \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    aeron-multicast-ping-demo-1.0-SNAPSHOT-jar-with-dependencies.jar
