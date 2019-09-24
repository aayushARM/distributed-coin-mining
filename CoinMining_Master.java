
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CoinMining_Master {

    /**
     * convert byte[] to hex string
     *
     * @param hash
     * @return hex string
     */
    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * get a sha256 of the input string
     *
     * @param inputString
     * @return resulting hash in hex string
     */
    public static String SHA256(String inputString) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return bytesToHex(sha256.digest(inputString.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            System.err.println(ex.toString());
            return null;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String blockHash = SHA256("CSCI-654 Foundations of Parallel Computing");
        System.out.println("BlockHash: " + blockHash);

        String targetHash = "000000038023b712892a41e8438e3ff2242a68747105de0395826f60b38d88dc";
        System.out.println("TargetHash: " + targetHash);
        int n_nodes = 10;
        int partitionSize = (int) (((long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE) / n_nodes);
        int basePort = 5000;
        //String[] hosts = {"grissom.cis.rit.edu", "glados.cs.rit.edu"};
        Thread[] nodeHandlers = new Thread[n_nodes];

        MasterNonceStatus masterNonceStatus = new MasterNonceStatus();
        long startTime = System.currentTimeMillis();
        System.out.println("Handing over work to nodes...");
        for (int i = 0; i < n_nodes; i++) {
            int node_nonce_min = Integer.MIN_VALUE + i * partitionSize, node_nonce_max;
            if (i == n_nodes - 1)
                node_nonce_max = Integer.MAX_VALUE;
            else
                node_nonce_max = Integer.MIN_VALUE + (i + 1) * partitionSize;
            nodeHandlers[i] = new Thread(new NodeHandler(i, blockHash, targetHash, node_nonce_min, node_nonce_max,
                    "luna0"+i+".cs.rit.edu", basePort + i, masterNonceStatus));
            nodeHandlers[i].start();
        }

        System.out.println("Waiting for nodes to find the nonce.");

        for(Thread t: nodeHandlers){
            t.join();
        }


        String[] results = masterNonceStatus.resultStr.split(",");
        System.out.println("Work complete, nonce found on Node "+ results[0] + " by Thread " +results[1]+ ".");
        System.out.println("Nonce value: "+ results[2]);
        System.out.println("Resultant Hash: "+ results[3]);
        System.out.println("Time taken: "+ (System.currentTimeMillis() - startTime) + " milliseconds.");
    }
}

class MasterNonceStatus{
    boolean nonceFound = false;
    String resultStr = "";
}

class NodeHandler implements Runnable{
    int nodeId, node_nonce_min, node_nonce_max, port;
    String blockHash, targetHash, host;
    MasterNonceStatus masterNonceStatus;
    NodeHandler(int nodeId, String blockHash, String targetHash, int node_nonce_min, int node_nonce_max, String host,
                int port, MasterNonceStatus masterNonceStatus){
        this.blockHash = blockHash;
        this.nodeId = nodeId;
        this.targetHash = targetHash;
        this.node_nonce_max = node_nonce_max;
        this.node_nonce_min = node_nonce_min;
        this.port = port;
        this.masterNonceStatus = masterNonceStatus;
        this.host = host;
    }

    @Override
    public void run() {
        Socket slave = null;
        try {
            slave = new Socket(host, port);
            slave.setSoTimeout(3000);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String dataStr = nodeId+","+blockHash+","+targetHash+","+ node_nonce_min +","+ node_nonce_max+"*";
        try {
            OutputStreamWriter osw= new OutputStreamWriter(slave.getOutputStream(), "UTF-8");
            osw.write(dataStr);
            osw.flush();
            InputStreamReader isr = new InputStreamReader(slave.getInputStream());

            while(!masterNonceStatus.nonceFound) {
                try {
                    String resultStr = "";
                    int char_int = isr.read();
                    while (char_int != -1) {
                        char theChar = (char) char_int;
                        resultStr += theChar;
                        char_int = isr.read();
                    }
                    masterNonceStatus.nonceFound = true;
                    masterNonceStatus.resultStr = resultStr;
                }
                catch (Exception e){
                    //Nonce found.
                    continue;
                }
            }

            osw.write("Y");
            osw.flush();


        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
