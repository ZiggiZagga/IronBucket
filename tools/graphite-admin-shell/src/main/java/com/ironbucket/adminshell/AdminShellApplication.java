package com.ironbucket.adminshell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@CommandScan
public class AdminShellApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminShellApplication.class, args);
    }
}
