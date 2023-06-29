import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * @author Lorenzo De Luca
 */
public class DyndnsUpdater{
    static File ipFile = new File("ip.txt");
    static String dns_zone_name="domain.pro";
    static long dns_record_id =  12345678L;
    static String dns_record_subdomain =  "subdomain";
    static String dns_record_target =  "";
    static String ovh_endpoint_url =  "https://eu.api.ovh.com/1.0";
    static String ovh_endpoint =  "ovh-net";
    static String ovh_application_key = "123";
    static String ovh_application_secret = "456";
    static String ovh_consumer_key =  "789";
    static int refreshMinutes=5;
    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static boolean ovhApiRequest(String target, String method,String body) throws IOException, NoSuchAlgorithmException{
        URL urlApiOvh = new URL(new StringBuilder(ovh_endpoint_url).append(target).toString());
        HttpURLConnection request = (HttpURLConnection) urlApiOvh.openConnection();
        request.setRequestMethod(method);
        request.setReadTimeout(30000);
        request.setConnectTimeout(30000);
        request.setRequestProperty("Content-Type", "application/json");
        request.setRequestProperty("X-Ovh-Application", ovh_application_key);
        long timestamp = System.currentTimeMillis() / 1000;
        String toSign = ovh_application_secret + "+" + ovh_consumer_key + "+" + method + "+" + urlApiOvh + "+" + body + "+" + timestamp;
        String signature = new StringBuilder("$1$").append(HashSHA1(toSign)).toString();
        request.setRequestProperty("X-Ovh-Consumer", ovh_consumer_key);
        request.setRequestProperty("X-Ovh-Signature", signature);
        request.setRequestProperty("X-Ovh-Timestamp", Long.toString(timestamp));
        request.setDoOutput(true);
        if(body!=""){
            DataOutputStream out = new DataOutputStream(request.getOutputStream());
            out.writeBytes(body);
            out.flush();
            out.close();
        }
        String inputLine;
        BufferedReader in;
        int responseCode = request.getResponseCode();
        if (responseCode == 200) {
            in = new BufferedReader(new InputStreamReader(request.getInputStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(request.getErrorStream()));
        }
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        System.out.println(Integer.toString(responseCode)+"-"+response);
        return (responseCode==200)?true:false;
    }

    /*
     * alternatives api to retrieve the public ip :
     *  - https://ipv4.icanhazip.com/
     *  - http://myexternalip.com/raw
     *  - http://ipecho.net/plain
     */
    public static void dynDnsUpdate() throws IOException, NoSuchAlgorithmException{
        FileWriter fw;
        Scanner fr;
        String oldIp;
        String currentIp;

        if(ipFile.createNewFile()){//create a new file if it doesnt exists yet
            fw = new FileWriter(ipFile,false);
            fw.write("x.x.x.x");
            fw.close();
        }

        //reading the old ip from the db file
        fr=new Scanner(ipFile);
        oldIp=fr.nextLine();
        fr.close();

        //get current ip
        String api = "http://checkip.amazonaws.com/";
        URL urlApiPublicIp = new URL(api);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(urlApiPublicIp.openStream()))) {
            currentIp = br.readLine();
        }

        if(!oldIp.equals(currentIp)){
            //api request dynHost update
            dns_record_target=currentIp;
            String url="/domain/zone/"+dns_zone_name+"/dynHost/record/"+dns_record_id;
            String body="{"+
                            "\"ip\":\""+dns_record_target+"\","+
                            "\"subDomain\":\""+dns_record_subdomain+"\""+"}";
            boolean success1=ovhApiRequest(url, "PUT",body);
            
            //api request name zone refresh
            url="/domain/zone/"+dns_zone_name+"/refresh";
            body="";
            boolean success2=ovhApiRequest(url, "POST",body);
            boolean successTOT=success1&&success2;

            //update ip.txt
            LocalDateTime now = LocalDateTime.now();  
            if(successTOT){
                fw = new FileWriter(ipFile,false);
                fw.write(dns_record_target);
                fw.close();

                System.out.println(dtf.format(now)+"---new ip:"+dns_record_target);  
            }else{
                System.out.println(dtf.format(now)+"---error");  
            }
            
        }
    }

    public static String HashSHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
	    MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");
        byte[] sha1hash = new byte[40];
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        sha1hash = md.digest();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < sha1hash.length; i++) {
            sb.append(Integer.toString((sha1hash[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
	}
    
    public static void main(String[] args) {
        final ScheduledExecutorService updateScheduler = Executors.newSingleThreadScheduledExecutor();

        updateScheduler.scheduleAtFixedRate(new Runnable(){
            @Override
            public void run(){
                try {
                    dynDnsUpdate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, refreshMinutes, TimeUnit.MINUTES);
    }
}