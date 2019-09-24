package edu.rit.cs;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CoinMining_Slave {

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


    public static void main(String[] args) throws IOException {
        ServerSocket ss = null;
        ss = new ServerSocket(Integer.parseInt(args[0]));
        while(true){
            System.out.println("Waiting for connection from master node...");
            Socket master = ss.accept();
            ss.setSoTimeout(3000);
            System.out.println("Master node connected.");
            InputStreamReader isr = new InputStreamReader(master.getInputStream());

            String dataStr = "";
            while(true) {
                try {
                    char char_i = (char) isr.read();
                    while (char_i != '*') {
                        dataStr += char_i;
                        char_i = (char) isr.read();
                    }
                    break;
                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("Data received.");
            String[] dataArr = dataStr.split(",");
            int nodeId = Integer.parseInt(dataArr[0]);
            String blockHash = dataArr[1];
            String targetHash = dataArr[2];
            int node_nonce_min = Integer.parseInt(dataArr[3]);
            int node_nonce_max = Integer.parseInt(dataArr[4]);

            SlaveNonceStatus slaveNonceStatus = new SlaveNonceStatus();
            // ToDo: Doesn't work for n_threads = 1.
            int n_cores = Runtime.getRuntime().availableProcessors();
            int n_threads = n_cores;
            int partitionSize = (int)(((long)node_nonce_max - (long)node_nonce_min)/n_threads);

            OutputStreamWriter osw = new OutputStreamWriter(master.getOutputStream());
            System.out.println("Partition Size: "+ partitionSize);
            Thread[] threads = new Thread[n_threads];
            System.out.println("Starting worker threads...");
            for(int i=0; i<n_threads; i++){
                int thread_nonce_min = node_nonce_min + i*partitionSize, thread_nonce_max;
                if(i == n_threads-1)
                    thread_nonce_max = node_nonce_max;
                else
                    thread_nonce_max = node_nonce_min + (i+1)*partitionSize;
                threads[i] = new Thread(new SlavePOWThread(nodeId,i, blockHash, targetHash, thread_nonce_min, thread_nonce_max,
                        slaveNonceStatus, osw));
                threads[i].start();
            }

            System.out.println("Waiting for nonce to be found.");
            while(!slaveNonceStatus.nonceFound) {
                try {
                    char nonceFoundMaster = (char) isr.read();
                    if(nonceFoundMaster == 'Y') {
                        slaveNonceStatus.nonceFound = true;
                        System.out.println("Nonce found, terminating all the threads.");
                    }
                }
                catch (Exception e){
                    continue;
                }
            }
        }

    }
}

class SlaveNonceStatus{
    boolean nonceFound = false;
    int foundByThread, nonceValue;
    String resultingHash = "";
}

class SlavePOWThread implements Runnable{

    int nonce_min, nonce_max, threadId, nodeId;
    String blockHash, targetHash;
    SlaveNonceStatus slaveNonceStatus;
    OutputStreamWriter osw;
    SlavePOWThread(int nodeId, int threadId, String blockHash, String targetHash, int nonce_min, int nonce_max,
                   SlaveNonceStatus nonceBool, OutputStreamWriter osw){
        this.blockHash = blockHash;
        this.targetHash = targetHash;
        this.nonce_min = nonce_min;
        this.nonce_max = nonce_max;
        this.threadId = threadId;
        this.nodeId = nodeId;
        this.slaveNonceStatus = nonceBool;
        this.osw = osw;
    }

    @Override
    public void run() {
        String tmp_hash = "undefined";
        for(int nonce=nonce_min; nonce<nonce_max; nonce++) {
            if(slaveNonceStatus.nonceFound){
                System.out.println("Nonce found, terminating Thread "+threadId);
                break;
            }

            tmp_hash = CoinMining_Slave.SHA256(CoinMining_Slave.SHA256(blockHash+String.valueOf(nonce)));
            if(targetHash.compareTo(tmp_hash)>0) {
                System.out.println("Nonce found by Thread "+threadId);
                slaveNonceStatus.nonceFound = true;
                slaveNonceStatus.foundByThread = threadId;
                slaveNonceStatus.resultingHash = tmp_hash;
                slaveNonceStatus.nonceValue = nonce;
                String resultStr = nodeId+","+slaveNonceStatus.foundByThread +","+slaveNonceStatus.nonceValue+","+slaveNonceStatus.resultingHash;
                try {
                    osw.write(resultStr);
                    osw.flush();
                    osw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }
}