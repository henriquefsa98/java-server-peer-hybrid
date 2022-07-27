import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        return resultados.toArray(new String[0]);

    }

    public static void imprimeClientesLogados(){   
        // Funcao que imprime a lista de clientes logados
        System.out.println("\n-------------------------\n\nClientes Logados:\n");

        for (Map.Entry<String, Mensagem> entry : clientesLogados.entrySet()) {
            System.out.println(entry.getKey()+":");
            entry.getValue().printMensagem();
        }

        System.out.println("\n------------------------------------------\n\n");

    }

    public static void main(String[] args) throws Exception{
        
        DatagramSocket serverSocket = new DatagramSocket(10098, InetAddress.getByName("127.0.0.1"));   // porta especifica!

        System.out.println("Server iniciado");
        System.out.println("Endereco: " + serverSocket.getLocalAddress().getHostAddress() + ", Porta: " + serverSocket.getLocalPort());

        while(true){

            byte[] receivedBuffer = new byte[1024];
            Gson gson = new Gson();

            DatagramPacket receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);

            // Aguarda o recebimento de algum pacote UDP
            serverSocket.receive(receivedPacket);

            // Serializa o pacote recebido (bytes) em uma instancia da Classe Mensagem 
            String stringClient = new String(receivedPacket.getData(), receivedPacket.getOffset(), receivedPacket.getLength());
            Mensagem msgClient = gson.fromJson(stringClient, Mensagem.class);


            // Captura as informacoes de rede do pacote recebido
            InetAddress clientAddress = receivedPacket.getAddress();
            int clientPort = receivedPacket.getPort();

            // Switch para tratar todos os tipos de requisicoes
            switch (msgClient.requisicao){

                case "JOIN":
                    // Caso receba um pedido de JOIN, inicia uma thread de Join enviando os dados necessários
                    JointhreadServer jts = new JointhreadServer(msgClient.ipv4client, clientPort, msgClient);
                    jts.start();
                    break;

                case "LEAVE":
                    // Caso receba um pedido de LEAVE, inicia uma thread de LEAVE enviando os dados necessários
                    LeavethreadServer leaveThread = new LeavethreadServer(clientPort, msgClient.udpport, clientAddress);
                    System.out.println("chave de clilog de saida: " + clientAddress.toString()+":"+msgClient.udpport);
                    System.out.println("Peer " + clientAddress.getHostAddress() + ":" + clientPort + " saiu. Eliminando seus arquivos " + Arrays.toString(clientesLogados.get(clientAddress.toString()+":"+msgClient.udpport).arquivos));
                    
                    leaveThread.start();
                    
                    break;

                case "SEARCH":
                    // Caso receba um pedido de SEARCH, inicia uma thread de Search enviando os dados necessários
                    SearchthreadServer sts = new SearchthreadServer(clientAddress, clientPort, msgClient.nomeArquivo);

                    System.out.println("Peer " + receivedPacket.getAddress().getHostAddress() + ":" + receivedPacket.getPort() + " solicitou arquivo " + msgClient.nomeArquivo);

                    sts.start();

                    break;

                case "UPDATE":
                    // Caso receba um pedido de UPDATE, inicia uma thread de Update enviando os dados necessários
                    UpdatethreadServer upserver = new UpdatethreadServer(msgClient, clientPort);

                    upserver.start();

                    break;

            }


        }

    }

    public static class UpdatethreadServer extends Thread{
        // Classe de Thread responsavel pelo update da lista de arquivos que um Peer possui

        // Dados necessarios: Mensagem de Update contendo o novo arquivo, e a porta UDP
        private Mensagem updateMsg;
        private int udpResponseport;

        public UpdatethreadServer(Mensagem upmsg, int udpp){

            updateMsg = upmsg;
            udpResponseport = udpp; // porta udp de update do peer
        }

        
        public void run(){

            // Verifica se a mensagem de Update esta correta
            if (!updateMsg.requisicao.equals("UPDATE") || updateMsg.nomeArquivo.equals("") || updateMsg.udpport==-1){

                System.out.println("Requisicao de update invalida, cancelando operacao!");
                return;

            }


            try{
                
                
                DatagramSocket s = new DatagramSocket();

                Mensagem updateOK = new Mensagem("UPDATE_OK");

                byte[] sendBuffer = new byte[1024];

                Gson gson = new Gson();

                sendBuffer = gson.toJson(updateOK).getBytes();

                DatagramPacket updateOKPacket = new DatagramPacket(sendBuffer, sendBuffer.length, updateMsg.ipv4client, udpResponseport);

                // Captura a mensagem atual do peer no servidor
                Mensagem updateClient = clientesLogados.get(updateMsg.ipv4client+":"+ updateMsg.udpport);
                
                // Cria uma nova lista de arquivos
                ArrayList<String> novosArquivos = new ArrayList<>();

                // Adciona os arquivos ja existentes na lista do server
                Collections.addAll(novosArquivos, updateClient.arquivos);

                // Adiciona o novo arquivo contido na mensagem de update
                novosArquivos.add(updateMsg.nomeArquivo);

                updateClient.arquivos = novosArquivos.toArray(new String[0]);

                // Substitui a lista antiga pela nova lista atualizada de arquivos do Peer no server
                clientesLogados.replace((updateMsg.ipv4client+":"+udpResponseport), updateClient);
                

                s.send(updateOKPacket);
                
                s.close();

                
            }
            catch(Exception e){
                System.out.println("erro no update:\n");
                e.printStackTrace();
                
            }
   

        }

    }


    public static class SearchthreadServer extends Thread{
        // Classe de Thread responsavel pela busca de arquivos na lista de Peers logados no server

        // Dados necessarios: porta udp, nome do arquivo procurado e endereco do peer
        private int udpport;
        private String arquivoProcurado;
        private InetAddress peerAddress;


        public SearchthreadServer(InetAddress clientaddr, int port, String arq){

            peerAddress = clientaddr;
            udpport = port;
            arquivoProcurado = arq;

        }

        public void run(){


            // Chama a funcao procuraArquivoNaLista(), que ira retornar uma lista dos peers que possuem o arquivo desejado
            String[] resultado = procuraArquivoNaLista(arquivoProcurado);

            // Cria mensagem de resposta, contendo a lista de peers que possuem o arquivo procurado
            Mensagem responseMsg = new Mensagem("SEARCH_OK", resultado);

            try {

                DatagramSocket socket = new DatagramSocket();

                Gson gson = new Gson();

                byte[] sendData = new byte[1024];

                // Serializa a mensagem de resposta, e transforma em um vetor de bytes
                sendData = gson.toJson(responseMsg).getBytes();

                DatagramPacket response = new DatagramPacket(sendData, sendData.length, peerAddress, udpport);

                socket.send(response);

                socket.close();


            } catch (Exception e) {

                //e.printStackTrace();
            }

        }

    }

    public static class LeavethreadServer extends Thread{
        // Classe de Thread responsavel por remover um cliente da lista

        // Dados necessarios
        private int leaveport; // para responder o leave
        private int udpport;   // para deletar o peer da lista de clientes logados
        private InetAddress peerAddress;

        public LeavethreadServer(int leave, int udp, InetAddress ip){

            leaveport = leave;
            udpport = udp;
            peerAddress = ip;

        }

        public void run(){

            // Cria a mensagem de resposta LEAVE_OK
            Mensagem leaveOK = new Mensagem("LEAVE_OK");

            byte[] sendData = new byte[1024];

            Gson gson = new Gson();

            sendData = gson.toJson(leaveOK).getBytes();

            DatagramPacket leaveOKPacket = new DatagramPacket(sendData, sendData.length, peerAddress, leaveport);          
            
            try {

                DatagramSocket socket = new DatagramSocket();

                // Utiliza o endereco do peer e a porta udp para criar a chave da lista de clientes logados
                String keyRemove = peerAddress + ":" + udpport;

                // Utiliza a chave criada para remover o cliente da lista
                removeCliente(keyRemove);

                socket.send(leaveOKPacket);

                socket.close();


            } catch (Exception e) {
                //e.printStackTrace();
            }

        }

    }


    public static class AlivethreadServer extends Thread{
        // Classe responsavel por manter o processo de alive para cada cliente logado 

        // Dados necessarios
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

                // Dorme por 30 segundos, para verificar se o peer ainda esta online
                sleep(30000);
                

                DatagramSocket serverSocket = new DatagramSocket();

                byte[] sendData = new byte[1024];
                byte[] responseData = new byte[1024];

                Gson gson = new Gson();

                DatagramPacket serverAlivePacket = new DatagramPacket(responseData, responseData.length);
                DatagramPacket peerAliveOK = new DatagramPacket(sendData, sendData.length);

                // Cria mensagem de alive
                Mensagem alive = new Mensagem("ALIVE");
                
                serverAlivePacket.setData(gson.toJson(alive).getBytes());
                serverAlivePacket.setAddress(peerAddress);
                serverAlivePacket.setPort(alivePort);


                serverSocket.send(serverAlivePacket);


                try{

                    // Aguarda por 1 segundo a resposta de ALIVE OK vinda do peer
                    serverSocket.setSoTimeout(1000);//  timeout de 1s, tempo maximo para o peer responder
                    serverSocket.receive(peerAliveOK);  

                    String peerText = new String(peerAliveOK.getData(), peerAliveOK.getOffset(), peerAliveOK.getLength());


                    Mensagem peerMsg = gson.fromJson(peerText, Mensagem.class);

                    // Verifica se a mensagem recebida corresponde ao ALIVE_OK
                    if (peerMsg.requisicao.equals("ALIVE_OK") != true){

                        Exception e = new Exception("Peer [IP]:[porta] morto. Eliminando seus arquivos [só nome dos arquivos]");
                        serverSocket.close();
                        throw e;

                    }
                    else{  

                        // Cria um novo thread de alive para o mesmo peer, para manter o processo de forma recursiva
                        AlivethreadServer recurrent = new AlivethreadServer(peerAddress, alivePort, udpport);
                        recurrent.start();

                    }

                } catch(Exception e) {

                    String offPeerKey = peerAddress.toString() + ":" + udpport;

                    System.out.println("Peer " + offPeerKey + " morto. Eliminando seus arquivos " + Arrays.toString(clientesLogados.get(offPeerKey).arquivos));
  
                    removeCliente(offPeerKey);

                }




            } catch (Exception e) {
                
                //e.printStackTrace();
            }

        }

    }


    public static class JointhreadServer extends Thread {
        // Classe de thread responsavel para cuidar do pedido de entrada do Peer

        private InetAddress peerAddress=null;
        private int clientport;
        private Mensagem joinMessage=null;
        private DatagramSocket serverSocket =null;

        public JointhreadServer(InetAddress clienaddr, int port, Mensagem msg){

            
            joinMessage = msg;
            peerAddress = clienaddr;
            clientport = port;

        }

        public void run(){

            // Valida se a requisicao de join esta completa, como a req correta, endereco ip e as devidas portas
            if (!joinMessage.requisicao.equals("JOIN") || joinMessage.ipv4client==null || joinMessage.udpport==-1 || joinMessage.tcpport==-1 || joinMessage.udpAlivePort==-1){  
                System.out.println("Requisicao invalida para join!");
                return;
            }


            byte[] responseData = new byte[1024];

            Gson gson = new Gson();

            //System.out.println("chave de clientelogados: " + joinMessage.ipv4client.toString() + ':' + joinMessage.udpport);

            clientesLogados.put((joinMessage.ipv4client.toString() + ':' + joinMessage.udpport), joinMessage);

            // Cria a mensagem de Join OK para enviar ao Peer
            Mensagem resposta = new Mensagem("JOIN_OK");

            responseData = gson.toJson(resposta).getBytes();

            DatagramPacket serverResponsePacket = new DatagramPacket(responseData, responseData.length, peerAddress, clientport);

            System.out.println("Peer " + peerAddress.getHostAddress() + ":" + clientport + " adicionado com arquivos " + Arrays.toString(joinMessage.arquivos));

            try {
                serverSocket = new DatagramSocket();
                serverSocket.send(serverResponsePacket);

                // Inicia o processo de alive, para garantir que o Peer recem conectado se matenha vivo
                AlivethreadServer verificadorAlive = new AlivethreadServer(peerAddress, joinMessage.udpAlivePort, joinMessage.udpport);
                verificadorAlive.start();  // inicia verificador alive

                serverSocket.close();

            } catch (IOException e) {
                //e.printStackTrace();
            }

        }

    } 

}