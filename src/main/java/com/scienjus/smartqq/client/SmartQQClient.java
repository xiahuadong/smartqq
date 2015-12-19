package com.scienjus.smartqq.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.scienjus.smartqq.callback.MessageCallback;
import com.scienjus.smartqq.model.Font;
import com.scienjus.smartqq.model.Group;
import com.scienjus.smartqq.model.GroupMessage;
import com.scienjus.smartqq.model.Message;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Api客户端
 * @author ScienJus
 * @date 2015/12/18.
 */
public class SmartQQClient {

    private static final Logger LOGGER = Logger.getLogger(SmartQQClient.class);

    //发送请求
    private CloseableHttpClient client = HttpClients.createDefault();

    //保存cookie信息
    private HttpClientContext context = HttpClientContext.create();

    //鉴权参数
    private String ptwebqq;

    private String vfwebqq;

    private long uin;

    private String psessionid;

    private long msgId = 43690001;

    public void login() {
        getQRCode();
        String url = verifyQRCode();
        getPtwebqq(url);
        getVfwebqq();
        getUinAndPsessionid();
    }

    //登录流程1：获取二维码
    private void getQRCode() {
        LOGGER.info("开始获取二维码");
        String filePath = getClass().getResource("/").getPath().concat("qrcode.png");
        HttpGet get = defaultHttpGet("https://ssl.ptlogin2.qq.com/ptqrshow?appid=501004106&e=0&l=M&s=5&d=72&v=4&t=" + Math.random(), null);
        try (CloseableHttpResponse response = client.execute(get, context);
             FileOutputStream out = new FileOutputStream(filePath)) {
            int len;
            byte[] buffer = new byte[1024];
            while ((len = response.getEntity().getContent().read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.close();
            LOGGER.info("二维码已保存在 " + filePath + " 文件中，请打开手机QQ并扫描二维码");
        } catch (IOException e) {
            LOGGER.error("获取二维码失败");
        }
    }

    //登录流程2：校验二维码
    private String verifyQRCode() {
        LOGGER.info("等待扫描二维码");
        HttpGet get = defaultHttpGet(
                "https://ssl.ptlogin2.qq.com/ptqrlogin?webqq_type=10&remember_uin=1&login2qq=1&aid=501004106&u1=h" +
                        "ttp%3A%2F%2Fw.qq.com%2Fproxy.html%3Flogin2qq%3D1%26webqq_type%3D10&ptredirect=0&ptlang=2052&daid=164&from_ui=1&ptty" +
                        "pe=1&dumy=&fp=loginerroralert&action=0-0-157510&mibao_css=m_webqq&t=1&g=1&js_type=0&js_ver=10143&login_sig=&pt_randsalt=0",

                "https://ui.ptlogin2.qq.com/cgi-bin/login?daid=164&target=self&style=16&mibao_css=m_webqq&appid=501004106&enable_q" +
                        "login=0&no_verifyimg=1&s_url=http%3A%2F%2Fw.qq.com%2Fproxy.html&f_url=loginerroralert&strong_login=1&login_state=10&t=20131024001");

        while (true) {
            sleep(1);
            try (CloseableHttpResponse response = client.execute(get, context)) {
                String responseText = getResponseText(response);
                if (responseText.contains("成功")) {
                    for (String content : responseText.split("','")) {
                        if (content.startsWith("http")) {
                            return content;
                        }
                    }
                } else if (responseText.contains("已失效")) {
                    LOGGER.info("二维码已失效，尝试重新获取二维码");
                    getQRCode();
                }
            } catch (IOException e) {
                LOGGER.error("校验二维码失败");
            }
        }

    }

    //登录流程3：获取ptwebqq
    private void getPtwebqq(String url) {
        LOGGER.info("开始获取ptwebqq");
        HttpGet get = defaultHttpGet(url, null);
        try {
            client.execute(get, context);
            for (Cookie cookie : context.getCookieStore().getCookies()) {
                if (cookie.getName().equals("ptwebqq")) {
                    this.ptwebqq = cookie.getValue();
                }
            }
        } catch (IOException e) {
            LOGGER.error("获取ptwebqq失败");
        }
    }

    //登录流程4：获取vfwebqq
    private void getVfwebqq() {
        LOGGER.info("开始获取vfwebqq");
        HttpGet get = defaultHttpGet(
                "http://s.web2.qq.com/api/getvfwebqq?ptwebqq=" + ptwebqq + "&clientid=53999199&psessionid=&t=" + System.currentTimeMillis(),
                "http://s.web2.qq.com/proxy.html?v=20130916001&callback=1&id=1");
        try (CloseableHttpResponse response = client.execute(get, context)) {
            JSONObject responseJson = JSON.parseObject(getResponseText(response));
            this.vfwebqq = responseJson.getJSONObject("result").getString("vfwebqq");
        } catch (IOException e) {
            LOGGER.error("获取vfwebqq失败");
        }
    }

    //登录流程5：获取uin和psessionid
    private void getUinAndPsessionid() {
        LOGGER.info("开始获取uin和psessionid");
        HttpPost post = defaultHttpPost(
                "http://d1.web2.qq.com/channel/login2",
                "http://d1.web2.qq.com/proxy.html?v=20151105001&callback=1&id=2");
        JSONObject r = new JSONObject();
        r.put("ptwebqq", ptwebqq);
        r.put("clientid", 53999199);
        r.put("psessionid", "");
        r.put("status", "online");
        try {
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString()))));
            try (CloseableHttpResponse response = client.execute(post, context)) {
                JSONObject responseJson = JSON.parseObject(getResponseText(response));
                this.psessionid = responseJson.getJSONObject("result").getString("psessionid");
                this.uin = responseJson.getJSONObject("result").getLong("uin");
            } catch (IOException e) {
                LOGGER.error("获取uin和psessionid失败");
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("获取uin和psessionid失败");
        }

    }

    /**
     * 获得群列表
     */
    public List<Group> getGroupList() {
        LOGGER.info("开始获取群列表");
        HttpPost post = defaultHttpPost(
                "http://s.web2.qq.com/api/get_group_name_list_mask2",
                "http://d1.web2.qq.com/proxy.html?v=20151105001&callback=1&id=2");
        JSONObject r = new JSONObject();
        r.put("vfwebqq", vfwebqq);
        r.put("hash", hash());
        try {
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString()))));
            try (CloseableHttpResponse response = client.execute(post, context)) {
                JSONObject responseJson = JSON.parseObject(getResponseText(response));
                return JSON.parseArray(responseJson.getJSONObject("result").getJSONArray("gnamelist").toJSONString(), Group.class);
            } catch (IOException e) {
                LOGGER.error("获取群列表失败");
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("获取群列表失败");
        }
        return Collections.emptyList();
    }

    public void pollMessage(MessageCallback callback) {
        LOGGER.info("开始接受消息");
        HttpPost post = defaultHttpPost(
                "http://d1.web2.qq.com/channel/poll2",
                "http://d1.web2.qq.com/proxy.html?v=20151105001&callback=1&id=2");
        JSONObject r = new JSONObject();
        r.put("ptwebqq", ptwebqq);
        r.put("clientid", 53999199);
        r.put("psessionid", psessionid);
        r.put("key", "");
        try {
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString()))));
            while (true) {
                sleep(1);
                try (CloseableHttpResponse response = client.execute(post, context)) {
                    JSONObject responseJson = JSON.parseObject(getResponseText(response));
                    if (responseJson.getInteger("retcode") == 0) {
                        JSONArray array = responseJson.getJSONArray("result");
                        for (int i = 0; array != null && i < array.size(); i++) {
                            JSONObject message = array.getJSONObject(i);
                            String type = message.getString("poll_type");
                            if ("message".equals(type)) {
                                callback.onMessage(new Message(message.getJSONObject("value")));
                            } else if ("group_message".equals(type)) {
                                callback.onGroupMessage(new GroupMessage(message.getJSONObject("value")));
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("获取接受消息失败");
                }
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("获取接受消息失败");
        }
    }

    public void sendMessageToGroup(long groupId, String msg) {
        LOGGER.info("开始发送群消息");
        HttpPost post = defaultHttpPost(
                "http://d1.web2.qq.com/channel/send_qun_msg2",
                "http://d1.web2.qq.com/proxy.html?v=20151105001&callback=1&id=2");
        JSONObject r = new JSONObject();
        r.put("group_uin", groupId);
        r.put("content", JSON.toJSONString(Arrays.asList(msg, Arrays.asList("font", Font.DEFAULT_FONT))));  //注意这里虽然格式是Json，但是实际是String
        r.put("face", 573);
        r.put("clientid", 53999199);
        r.put("msg_id", msgId++);
        r.put("psessionid", psessionid);
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString())), "UTF-8");
            post.setEntity(entity);
            try (CloseableHttpResponse response = client.execute(post, context)) {
                JSONObject responseJson = JSON.parseObject(getResponseText(response));
                if (0 == responseJson.getInteger("errCode")) {
                    LOGGER.error("发送群消息成功");
                } else {
                    LOGGER.error("发送群消息失败");
                }
            } catch (IOException e) {
                LOGGER.error("发送群消息失败");
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("发送群消息失败");
        }
    }

    public void sendMessageToFriend(long friendId, String msg) {
        LOGGER.info("开始发送消息");
        HttpPost post = defaultHttpPost(
                "http://d1.web2.qq.com/channel/send_buddy_msg2",
                "http://d1.web2.qq.com/proxy.html?v=20151105001&callback=1&id=2");
        JSONObject r = new JSONObject();
        r.put("to", friendId);
        r.put("content", JSON.toJSONString(Arrays.asList(msg, Arrays.asList("font", Font.DEFAULT_FONT))));  //注意这里虽然格式是Json，但是实际是String
        r.put("face", 573);
        r.put("clientid", 53999199);
        r.put("msg_id", msgId++);
        r.put("psessionid", psessionid);
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString())), "UTF-8");
            post.setEntity(entity);
            try (CloseableHttpResponse response = client.execute(post, context)) {
                JSONObject responseJson = JSON.parseObject(getResponseText(response));
                if (0 == responseJson.getInteger("errCode")) {
                    LOGGER.error("发送消息成功");
                } else {
                    LOGGER.error("发送消息失败");
                }
            } catch (IOException e) {
                LOGGER.error("发送消息失败");
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("发送消息失败");
        }
    }

    public void getFriendList() {
        LOGGER.info("开始获取好友列表");
        HttpPost post = defaultHttpPost(
                "http://s.web2.qq.com/api/get_user_friends2",
                "http://s.web2.qq.com/proxy.html?v=20130916001&callback=1&id=1");
        JSONObject r = new JSONObject();
        r.put("vfwebqq", vfwebqq);
        r.put("hash", hash());
        try {
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString()))));
            try (CloseableHttpResponse response = client.execute(post, context)) {
                JSONObject responseJson = JSON.parseObject(getResponseText(response));
            } catch (IOException e) {
                LOGGER.error("获取好友列表失败");
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("获取好友列表失败");
        }
    }

    private String hash() {
        return hash(uin, ptwebqq);
    }

    private static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {}
    }

    private static String hash(long x, String K) {
        int[] N = new int[4];
        for (int T = 0; T < K.length(); T++) {
            N[T % 4] ^= K.charAt(T);
        }
        String[] U = {"EC", "OK"};
        long[] V = new long[4];
        V[0] = x >> 24 & 255 ^ U[0].charAt(0);
        V[1] = x >> 16 & 255 ^ U[0].charAt(1);
        V[2] = x >> 8 & 255 ^ U[1].charAt(0);
        V[3] = x & 255 ^ U[1].charAt(1);

        long[] U1 = new long[8];

        for (int T = 0; T < 8; T++) {
            U1[T] = T % 2 == 0 ? N[T >> 1] : V[T >> 1];
        }

        String[] N1 = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
        String V1 = "";
        for (long aU1 : U1) {
            V1 += N1[(int) ((aU1 >> 4) & 15)];
            V1 += N1[(int) (aU1 & 15)];
        }
        return V1;
    }

    private static String getResponseText(CloseableHttpResponse response) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            return buffer.toString();
        } catch (IOException e) {
            LOGGER.error("获取返回数据失败");
        }
        return "";
    }

    private static HttpGet defaultHttpGet(String url, String referer) {
        HttpGet get = new HttpGet(url);
        if (referer != null) {
            get.setHeader("Referer", referer);
        }
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36");
        return get;
    }

    private static HttpPost defaultHttpPost(String url, String referer) {
        HttpPost post = new HttpPost(url);
        if (referer != null) {
            post.setHeader("Referer", referer);
        }
        post.setHeader("Origin", getOrigin(url));
        post.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36");
        return post;
    }

    private static String getOrigin(String url) {
        return url.substring(0, url.lastIndexOf("/"));
    }

}
