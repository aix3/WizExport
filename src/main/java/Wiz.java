import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Wiz {

    private static final String WIZ_AS = "https://as.wiz.cn/as/user/login";
    private static final int TIMEOUT = 60 * 1000;

    private HttpClient client;
    private String kbServer, kbGuid;

    Wiz() {
        this.client = HttpClients.custom()
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build())
                .build();
    }

    /**
     * post /as/user/login
     * body: {
     * userId,
     * password,
     * }
     */
    public Wiz login(String userId, String password) throws IOException {
        HttpPost post = new HttpPost(WIZ_AS);
        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        post.setEntity(new StringEntity(String.format("{\"userId\":\"%s\", \"password\":\"%s\"}", userId, password), StandardCharsets.UTF_8));

        HttpResponse response = this.client.execute(post);
        String json = EntityUtils.toString(response.getEntity());
        Object token = JSONPath.extract(json, "result.token");
        if (token == null) {
            throw new RuntimeException("login error: " + json);
        }

        List<Header> headers = new ArrayList<>(1);
        headers.add(new BasicHeader("X-Wiz-Token", token.toString()));
        this.client = HttpClients.custom()
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .setDefaultHeaders(headers)
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build())
                .build();
        this.kbServer = JSONPath.extract(json, "result.kbServer").toString();
        this.kbGuid = JSONPath.extract(json, "result.kbGuid").toString();
        return this;
    }

    /**
     * get /ks/category/all/:kbGuid
     */
    void downloadTo(String dir) throws IOException {
        HttpResponse response = client.execute(new HttpGet(String.format("%s/ks/category/all/%s", kbServer, kbGuid)));
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException(EntityUtils.toString(response.getEntity()));
        }
        Object result = JSONPath.extract(EntityUtils.toString(response.getEntity()), "result");
        for (Object folder : (JSONArray) result) {
            downloadFolder(dir, folder.toString());
        }
    }

    /**
     * get /ks/note/list/category/:kbGuid?category=:folder&withAbstract=true|false&start=:start&count=:count&orderBy=title|created|modified&ascending=asc|desc
     */
    private void downloadFolder(String dir, String folder) throws IOException {
        String url = String.format(
                "%s/ks/note/list/category/%s?category=%s&withAbstract=false&start=0&count=100&orderBy=created&ascending=desc",
                kbServer, kbGuid, URLEncoder.encode(folder, "UTF-8")
        );
        HttpResponse docListResp = client.execute(new HttpGet(url));
        String docListJson = EntityUtils.toString(docListResp.getEntity());

        Path docPath = Files.createDirectories(Paths.get(dir, folder));
        Object docList = JSONPath.extract(docListJson, "result");
        for (Object doc : (JSONArray) docList) {
            downloadDoc(docPath, (JSONObject) doc);
        }
    }

    /**
     * get /ks/note/view/:kbGuid/:docGuid/
     */
    private void downloadDoc(Path docPath, JSONObject doc) {
        String docGuid = doc.get("docGuid").toString();
        String docTitle = doc.get("title").toString();
        try {
            String url = String.format("%s/ks/note/view/%s/%s", kbServer, kbGuid, docGuid);
            HttpResponse docHtmlResp = client.execute(new HttpGet(url));
            String docHtml = EntityUtils.toString(docHtmlResp.getEntity());

            docTitle = processDocTitle(docTitle);
            if (docTitle.endsWith(".md")) {
                String markdown = htmlToMarkdown(docHtml);
                Path docFile = Files.createFile(docPath.resolve(docTitle));
                Files.write(docFile, markdown.getBytes(StandardCharsets.UTF_8));
                normaliseMarkdown(docFile);
            } else {
                Path file = Files.createFile(docPath.resolve(docTitle + ".html"));
                Files.write(file, docHtml.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            System.out.println(String.format("Download %s fail, %s", docTitle, e.toString()));
        }
    }

    private String processDocTitle(String docTitle) {
        docTitle = docTitle.replace("|", "");
        docTitle = docTitle.replace("/", "");
        docTitle = docTitle.replace(">", "");
        docTitle = docTitle.replace(":", "");
        docTitle = docTitle.replace("“", "");
        docTitle = docTitle.replace("\"", "");
        return docTitle;
    }

    private void normaliseMarkdown(Path docFile) throws IOException {
        List<String> lines = Files.readAllLines(docFile);
        Files.delete(docFile);
        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(new File(docFile.toUri())))) {
            for (String line : lines) {
                String newLine = line;
                if (line.length() > 2) {
                    newLine = line.substring(2);
                }
                newLine = newLine.replaceAll(" ", " ");
                fileWriter.write(newLine);
                fileWriter.newLine();
            }
        }
    }

    private String htmlToMarkdown(String docHtml) {
        Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(true);
        outputSettings.charset(StandardCharsets.UTF_8);
        return Jsoup.parse(Jsoup.parse(docHtml).normalise().outputSettings(outputSettings).outerHtml()).wholeText();
    }
}
