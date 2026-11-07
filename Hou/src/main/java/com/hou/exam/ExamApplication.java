package com.hou.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.hou.exam.mapper")
public class ExamApplication {

    private static final String UNIX_DOMAIN_TMPDIR = "jdk.net.unixdomain.tmpdir";

    public static void main(String[] args) throws IOException {
        // Windows 用户名含空格时临时目录是 DENNIS~1 这样的短路径，Tomcat 的 NIO 管道会启动失败，
        // 所以把管道文件放到工作目录下
        if (System.getProperty(UNIX_DOMAIN_TMPDIR) == null) {
            Path dir = Files.createDirectories(Path.of("data", "tmp")).toAbsolutePath();
            System.setProperty(UNIX_DOMAIN_TMPDIR, dir.toString());
        }
        SpringApplication.run(ExamApplication.class, args);
    }
}
