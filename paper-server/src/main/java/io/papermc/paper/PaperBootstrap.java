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
    private static final String ANSI_YELLOW = "\033[1;33m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process scriptProcess;
    
    // 保留必要的环境变量，删除与脚本重复的参数
    private static final String[] RETAINED_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NAME", "DISABLE_ARGO"
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
            // 检查当前工作目录
            Path currentDir = Paths.get("").toAbsolutePath();
            System.out.println(ANSI_YELLOW + "Current working directory: " + currentDir + ANSI_RESET);
            
            // 列出当前目录文件，帮助调试
            System.out.println(ANSI_YELLOW + "Files in current directory:" + ANSI_RESET);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
                for (Path file : stream) {
                    System.out.println(ANSI_YELLOW + "  " + file.getFileName() + ANSI_RESET);
                }
            } catch (IOException e) {
                System.err.println(ANSI_RED + "Error listing directory: " + e.getMessage() + ANSI_RESET);
            }
            
            runScript();
            
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

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
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
    
    private static void runScript() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        // 查找脚本文件
        Path scriptPath = findScriptFile();
        if (scriptPath == null) {
            throw new FileNotFoundException("Could not find any .log script file in current directory");
        }
        
        System.out.println(ANSI_YELLOW + "Found script file: " + scriptPath + ANSI_RESET);
        
        // 确保脚本有执行权限
        if (!scriptPath.toFile().canExecute()) {
            System.out.println(ANSI_YELLOW + "Setting execute permission for script..." + ANSI_RESET);
            if (!scriptPath.toFile().setExecutable(true)) {
                System.err.println(ANSI_RED + "Warning: Failed to set execute permission for script" + ANSI_RESET);
            }
        }
        
        // 使用bash执行.log脚本文件
        ProcessBuilder pb = new ProcessBuilder("bash", scriptPath.toAbsolutePath().toString());
        pb.directory(new File(".")); // 设置工作目录为当前目录
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        System.out.println(ANSI_YELLOW + "Starting script with command: bash " + scriptPath + ANSI_RESET);
        scriptProcess = pb.start();
        
        // 等待脚本启动
        Thread.sleep(2000);
    }
    
    private static Path findScriptFile() {
        Path currentDir = Paths.get("").toAbsolutePath();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir, "*.log")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file) && !Files.isDirectory(file)) {
                    return file;
                }
            }
        } catch (IOException e) {
            System.err.println(ANSI_RED + "Error searching for script files: " + e.getMessage() + ANSI_RESET);
        }
        
        // 如果没有找到.log文件，尝试其他可能的脚本文件
        String[] possibleScripts = {"script.log", "run.log", "start.log", "boot.log"};
        for (String script : possibleScripts) {
            Path possiblePath = currentDir.resolve(script);
            if (Files.exists(possiblePath) && Files.isRegularFile(possiblePath)) {
                return possiblePath;
            }
        }
        
        return null;
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        // 只保留基本参数，删除与脚本重复的参数
        envVars.put("UUID", "a217d527-bd5e-4ef0-b899-d36627af0ddd");
        envVars.put("FILE_PATH", "./logs");
        envVars.put("NAME", "MC");
        envVars.put("DISABLE_ARGO", "false");
        
        // 只从系统环境变量读取保留的参数
        for (String var : RETAINED_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        // 从.env文件读取保留的参数
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            System.out.println(ANSI_YELLOW + "Loading environment variables from .env file" + ANSI_RESET);
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
                    
                    if (Arrays.asList(RETAINED_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                        System.out.println(ANSI_YELLOW + "Loaded from .env: " + key + "=" + value + ANSI_RESET);
                    }
                }
            }
        }
        
        // 打印加载的环境变量用于调试
        System.out.println(ANSI_YELLOW + "Environment variables to pass to script:" + ANSI_RESET);
        for (String key : RETAINED_ENV_VARS) {
            if (envVars.containsKey(key)) {
                System.out.println(ANSI_YELLOW + "  " + key + "=" + envVars.get(key) + ANSI_RESET);
            }
        }
    }
    
    private static void stopServices() {
        if (scriptProcess != null && scriptProcess.isAlive()) {
            scriptProcess.destroy();
            try {
                // 等待进程结束
                if (scriptProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.out.println(ANSI_RED + "Script process terminated gracefully" + ANSI_RESET);
                } else {
                    scriptProcess.destroyForcibly();
                    System.out.println(ANSI_RED + "Script process force terminated" + ANSI_RESET);
                }
            } catch (InterruptedException e) {
                scriptProcess.destroyForcibly();
                System.out.println(ANSI_RED + "Script process interrupted and terminated" + ANSI_RESET);
            }
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
