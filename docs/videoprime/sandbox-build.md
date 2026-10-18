# Compilar el extractor en un sandbox sin jitpack.io ni dl.google.com

```bash
apt-get install -y openjdk-17-jdk-headless
V=$(sed -n 's/^teamnewpipe-nanojson = "\(.*\)"/\1/p' ../newpipeextractor/gradle/libs.versions.toml)
git clone https://github.com/TeamNewPipe/nanojson.git && cd nanojson && git checkout "$V"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -q -B package -DskipTests -Dcheckstyle.skip -Dgpg.skip -Dmaven.javadoc.skip
cat > /tmp/nanojson.pom <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>com.github.TeamNewPipe</groupId><artifactId>nanojson</artifactId>
<version>$V</version><packaging>jar</packaging></project>
POM
mvn -q -B install:install-file -Dfile=target/nanojson-1.8-SNAPSHOT.jar -DpomFile=/tmp/nanojson.pom
```

`~/.gradle/init.d/mavenlocal.gradle`:

```groovy
beforeSettings { s -> s.dependencyResolutionManagement { repositories { mavenLocal() } } }
settingsEvaluated { s ->
    s.dependencyResolutionManagement.repositories.removeIf { r ->
        r.hasProperty('url') && (r.url.toString().contains('dl.google.com') || r.url.toString().contains('jitpack.io'))
    }
}
```

No sirve para la app Android: esa necesita acceso real a dl.google.com.
