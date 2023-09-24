#! /bin/sh

java \
        -XX:+UseBiasedLocking \
        -XX:BiasedLockingStartupDelay=0 \
        -XX:+UnlockExperimentalVMOptions \
        -XX:+TrustFinalNonStaticFields \
        -XX:+UnlockDiagnosticVMOptions \
        -XX:GuaranteedSafepointInterval=300000 \
        -XX:+UseParallelGC \
        -Daeron.event.cluster.log=all \
        -Daeron.event.cluster.log.disable=CANVASS_POSITION,APPEND_POSITION,COMMIT_POSITION \
        -Daeron.cluster.tutorial.nodeId=${1} \
        ${JVM_OPTS} ${ADD_OPENS} -jar \
	--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
	aeron-cluster-demo-1.0-SNAPSHOT-jar-with-dependencies.jar > logs/cluster-${1}.log &