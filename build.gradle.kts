plugins {
    id("java")
}

group = "com.cxuy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("com.google.code.gson:gson:2.13.1")
}

// 添加-parameters编译参数
tasks.compileJava {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}