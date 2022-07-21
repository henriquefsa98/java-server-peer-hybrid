import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;


public class Servidor{


    // Implementar o concurrentmap para guardar os clientes que deram join no server!
    // Guardar a mensagem de join, ja que ela contem todas as infos que precisamos!
    public static Map<String, Mensagem> clientesLogados = new ConcurrentHashMap<>();

    public static void removeCliente(String key){
        /**
        * Remove um cliente do ConcurrentHashMap de clientes logados. A key é  
        */

        clientesLogados.remove(key);
        //imprimeClientesLogados();

    }

    public static String[] procuraArquivoNaLista(String nomeArquivo){

        ArrayList<String> resultados = new ArrayList<String>();

        for (Map.Entry<String, Mensagem> entry : clientesLogados.entrySet()) {
            //System.out.println(entry.getKey()+":"+ Arrays.toString(entry.getValue().arquivos) + "\n\n");

            if (Arrays.toString(entry.getValue().arquivos).contains(nomeArquivo)){

                resultados.add(entry.getValue().ipv4client.toString() + ":" + entry.getValue().tcpport);

            }
        }

        //System.out.println("Server: Lista de clientes que possuem o arquivo " + nomeArquivo + ": ");

        //System.out.println(Arrays.toString(resultados.toArray(new String[0])));

        return resultados.toArray(new String[0]);

    }

    public static void imprimeClientesLogados(){   // criando essa func para chamar em todos os momentos que o hashmap for modificado!

        System.out.println("\n-------------------------\n\nClientes Logados:\n");

        // imprimir o mapa aqui pra ver se esta funcionando!
        for (Map.Entry<String, Mensagem> entry : clientesLogados.entrySet()) {
            System.out.println(entry.getKey()+":");
            entry.getValue().printMensagem();
        }

        System.out.println("\n------------------------------------------\n\n");

    }

    public static void main(String[] args) throws Exception{
        
        DatagramSocket serverSocket = new DatagramSocket(10098, InetAddress.getByName("127.0.0.1"));   // porta especifica!

        System.out.println("Server iniciado");
        System.out.println("Endereco: " + serverSocket.getLocalAddress() + ", Porta: " + serverSocket.getLocalPort());

        while(true){

            byte[] receivedBuffer = new byte[1024];
            //byte[] sendBuffer = new byte[1024];
            Gson gson = new Gson();

            DatagramPacket receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);


            System.out.println("Aguardando pacote...");

            serverSocket.receive(receivedPacket);

            String stringClient = new String(receivedPacket.getData(), receivedPacket.getOffset(), receivedPacket.getLength());

            Mensagem msgClient = gson.fromJson(stringClient, Mensagem.class);

            //System.out.println(msgClient);
            //System.out.println("\nMensagem recebida do cliente:\n");
            //msgClient.printMensagem();
            //System.out.println();

            InetAddress clientAddress = receivedPacket.getAddress();
            int clientPort = receivedPacket.getPort();

            //System.out.println("Endereco do peer: " + clientAddress);
            //System.out.println("Ednereco getSocketAddress do pck do peer: " + receivedPacket.getSocketAddress());

            switch (msgClient.requisicao){

                case "JOIN":

                    JointhreadServer jts = new JointhreadServer(serverSocket, msgClient.ipv4client, clientPort, msgClient);
                    jts.start();
                    break;

                case "LEAVE":

                    LeavethreadServer leaveThread = new LeavethreadServer(clientPort, msgClient.udpport, clientAddress);
                    leaveThread.start();

                    break;

                case "SEARCH":

                    SearchthreadServer sts = new SearchthreadServer(clientAddress, clientPort, msgClient.nomeArquivo);

                    sts.start();

                    break;

            }


        }

    }

    public static class SearchthreadServer extends Thread{

        private int udpport;
        private String arquivoProcurado;
        private InetAddress peerAddress;


        public SearchthreadServer(InetAddress clientaddr, int port, String arq){

            peerAddress = clientaddr;
            udpport = port;
            arquivoProcurado = arq;

        }

        public void run(){

            String[] resultado = procuraArquivoNaLista(arquivoProcurado);

            Mensagem responseMsg = new Mensagem("SEARCH_OK", resultado);

            try {

                DatagramSocket socket = new DatagramSocket();

                Gson gson = new Gson();

                byte[] sendData = new byte[1024];

                sendData = gson.toJson(responseMsg).getBytes();

                DatagramPacket response = new DatagramPacket(sendData, sendData.length, peerAddress, udpport);

                socket.send(response);

                socket.close();


            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    public static class LeavethreadServer extends Thread{

        private int leaveport; // para responder o leave
        private int udpport;   // para deletar o peer da lista de clientes logados
        private InetAddress peerAddress;

        public LeavethreadServer(int leave, int udp, InetAddress ip){

            leaveport = leave;
            udpport = udp;
            peerAddress = ip;

        }

        public void run(){

            Mensagem leaveOK = new Mensagem("LEAVE_OK");

            byte[] sendData = new byte[1024];

            Gson gson = new Gson();

            sendData = gson.toJson(leaveOK).getBytes();

            DatagramPacket leaveOKPacket = new DatagramPacket(sendData, sendData.length, peerAddress, leaveport);          
            
            try {

                DatagramSocket socket = new DatagramSocket();

                socket.setSoTimeout(500);

                socket.send(leaveOKPacket);

                String keyRemove = peerAddress + ":" + udpport;

                removeCliente(keyRemove);

                socket.close();


            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }





        }


    }

    public static class AlivethreadServer extends Thread{

        private InetAddress peerAddress;
        private int alivePort;
        private int udpport; // para conseguir remover da lista

        public AlivethreadServer(InetAddress peer, int aport, int udpp){

            peerAddress = peer;
            alivePort = aport;
            udpport = udpp;

        }

        public void run(){

            try {

                //System.out.println("Thread alive indo pro sleep");
                sleep(5000);  // sleep de 50 ms
                //System.out.println("Thread alive acordou!");

                DatagramSocket serverSocket = new DatagramSocket();

                byte[] sendData = new byte[1024];
                byte[] responseData = new byte[1024];

                Gson gson = new Gson();

                DatagramPacket serverAlivePacket = new DatagramPacket(responseData, responseData.length);
                DatagramPacket peerAliveOK = new DatagramPacket(sendData, sendData.length);

                Mensagem alive = new Mensagem("ALIVE");
                
                serverAlivePacket.setData(gson.toJson(alive).getBytes());
                serverAlivePacket.setAddress(peerAddress);
                serverAlivePacket.setPort(alivePort);


                serverSocket.send(serverAlivePacket);


                try{

                    serverSocket.setSoTimeout(500);//  timeout de 500 ms
                    serverSocket.receive(peerAliveOK);  

                    String peerText = new String(peerAliveOK.getData(), peerAliveOK.getOffset(), peerAliveOK.getLength());


                    Mensagem peerMsg = gson.fromJson(peerText, Mensagem.class);

                    if (peerMsg.requisicao.equals("ALIVE_OK") != true){

                        Exception e = new Exception("Peer [IP]:[porta] morto. Eliminando seus arquivos [só nome dos arquivos]");
                        serverSocket.close();
                        throw e;

                    }
                    else{  

                        //System.out.println("Peer: " + peerAliveOK.getAddress() + ':' + peerAliveOK.getPort() + " esta alive!");

                        // alive recursivo!
                        AlivethreadServer recurrent = new AlivethreadServer(peerAddress, alivePort, udpport);
                        recurrent.start();

                    }

                } catch(Exception e) {

                    String offPeerKey = peerAddress.toString() + ":" + udpport;

                    System.out.println("Peer " + offPeerKey + " morto. Eliminando seus arquivos " + Arrays.toString(clientesLogados.get(offPeerKey).arquivos));
  
                    removeCliente(offPeerKey);

                }




            } catch (Exception e) {
                // TODO Auto-generated catch block
                //e.printStackTrace();
            }

        }

    }

    public static class JointhreadServer extends Thread {

        private InetAddress peerAddress=null;
        private int clientport;
        private Mensagem joinMessage=null;
        private DatagramSocket serverSocket =null;

        public JointhreadServer(DatagramSocket serversocket, InetAddress clienaddr, int port, Mensagem msg){

            serverSocket = serversocket;
            joinMessage = msg;
            peerAddress = clienaddr;
            clientport = port;

        }

        public void run(){

            // fazer as demais validações de join, como lista de arqs!
            if (joinMessage.requisicao.equals("JOIN") == false){  
                System.out.println("Requisicao invalida para join!");
                joinMessage.printMensagem();
                System.out.println(joinMessage.requisicao + " != JOIN = " + (joinMessage.requisicao != "JOIN"));
                return;
            }

            System.out.println("Endereco do peer no threadjoinserver: " + peerAddress);


            byte[] responseData = new byte[1024];

            Gson gson = new Gson();

            clientesLogados.put((joinMessage.ipv4client.toString() + ':' + joinMessage.udpport), joinMessage);

            Mensagem resposta = new Mensagem("JOIN_OK");

            responseData = gson.toJson(resposta).getBytes();

            DatagramPacket serverResponsePacket = new DatagramPacket(responseData, responseData.length, peerAddress, clientport);

            System.out.println("endereco do cliente: " + serverResponsePacket.getSocketAddress() + clientport);

            try {

                serverSocket.send(serverResponsePacket);

                //imprimeClientesLogados();

                AlivethreadServer verificadorAlive = new AlivethreadServer(peerAddress, joinMessage.udpAlivePort, joinMessage.udpport);
                verificadorAlive.start();  // inicia verificador alive

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }


        }

    } 

}