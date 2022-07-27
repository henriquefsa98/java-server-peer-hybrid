import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class Peer {

    public static File pastaArquivos;
    
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        InetAddress peerIPV4 = InetAddress.getByName("127.0.0.1");
        
        DatagramSocket peerSocket = new DatagramSocket(0,peerIPV4);  // udp

        DatagramSocket aliveSocket = new DatagramSocket(0,peerIPV4);

        ServerSocket tcpSocket = new ServerSocket(0, 0, peerIPV4);

        DownloadController clientDC = new DownloadController(tcpSocket);  // tcp

        String pastapath;

        clientDC.start();

        Alivethread alive = new Alivethread(aliveSocket);    // alive thread
        alive.start();


        System.out.println("address inicial: " + peerSocket.getLocalAddress() + ":" + peerSocket.getLocalPort());

        InetAddress serverAddress = InetAddress.getByName("127.0.0.1");


        // Pede ao usuário que digite o caminho da pasta de arquivos desejada
        System.out.println("Por favor, digite o caminho da pasta que contem os arquivos, e que recebera os downloads: ");

        pastapath = sc.nextLine();
        pastaArquivos = new File(pastapath);


        // verifica se a pasta de arquivos existe, se nao, cria
        if(!pastaArquivos.exists()){
            pastaArquivos.mkdirs();
        }

        // Gera uma lista com todos os arquivos contidos na pasta informada pelo usuario
        File[] arquivosF;

        ArrayList<String> nomeArquivos = new ArrayList<String>();

        arquivosF = pastaArquivos.listFiles();

        for(int i=0; i < arquivosF.length; i++){
            if(arquivosF[i].isFile()){
                nomeArquivos.add(arquivosF[i].getName());
            }
        }

        // Imprime o nome de todos os arquivos contidos na pasta informada pelo usuario!
        for (String pathname : nomeArquivos) {
            System.out.println(pathname);
        }


        String comando;    // Entrada do console do usuario

        while(true){

            System.out.println("Digite um comando: ");

            comando = sc.nextLine();

            switch (comando.toUpperCase()){

                case "JOIN":
                    // Caso comando join, inicia a thread responsável para entrar no servidor
                    Mensagem joinMessage = new Mensagem("JOIN", peerIPV4, peerSocket.getLocalPort(), aliveSocket.getLocalPort(), tcpSocket.getLocalPort(), nomeArquivos.stream().toArray(String[] :: new));

                    Jointhread jointhread = new Jointhread(peerSocket, serverAddress, joinMessage);

                    System.out.println("JOIN req sent: ");

                    jointhread.start();

                    break;

                case "LEAVE":
                    // Caso comando leave, inicia a thread responsável para sair do servidor
                    Mensagem leaveMsg = new Mensagem("LEAVE", peerSocket.getLocalPort());

                    Leavethread leaveThread = new Leavethread(serverAddress, leaveMsg);

                    leaveThread.start();

                    System.out.println("LEAVE");
                    break;

                case "SEARCH":
                    // Caso comando SEARCH, inicia a thread responsável para procurar arquivos no servidor
                    String arqprocurado = new String();

                    System.out.println("Por favor digite o nome do arquivo procurado:");

                    arqprocurado = sc.nextLine();

                    Searchthread st = new Searchthread(arqprocurado, serverAddress);

                    st.start();

                    break;

                case "DOWNLOAD":
                    // Caso comando DOWNLOAD, inicia a thread responsável para fazer download de arquivo em outro peer
                    String arqname;
                    InetAddress peerServerAddress;
                    int peerServerPort;

                    System.out.println("Por Favor digite o nome do arquivo procurado:");
                    arqname = sc.nextLine();

                    System.out.println("Por Favor digite o endereço do peer que ira fornecer o arquivo:");
                    peerServerAddress = InetAddress.getByName(sc.nextLine());

                    System.out.println("Por Favor digite a porta do peer que ira fornecer o arquivo: ");
                    peerServerPort =  Integer. parseInt(sc.nextLine());

                    DownloadReceiveThread drt = new DownloadReceiveThread(peerServerPort, peerServerAddress, serverAddress, arqname, peerSocket.getLocalPort());
                    drt.start();

                    break;

                case "EXIT":
                    // Caso comando EXIT, fecha o Socket e finaliza o programa
                    System.out.println("Encerrando o programa!");
                    sc.close();

                    return;

            }
        }

    }


    public static class Searchthread extends Thread{
        // Thread responsavel pela busca de arquivos no servidor
        private String arquivoProcurado;
        private int tentativas=0;
        private InetAddress serverAddress;

        public Searchthread(String arq, InetAddress saddr){

            arquivoProcurado = arq;
            serverAddress = saddr;

        }

        public void run(){

            while (tentativas < 3){

                try {

                    DatagramSocket socket = new DatagramSocket();
                    
                    // Cria mensagem com o nome do arquivo procurado
                    Mensagem clientMsg = new Mensagem("SEARCH", arquivoProcurado, 0);

                    Gson gson = new Gson();

                    byte[] sendData = new byte[1024];
                    byte[] responseData = new byte[1024];

                    sendData = gson.toJson(clientMsg).getBytes();

                    DatagramPacket request = new DatagramPacket(sendData, sendData.length, serverAddress, 10098);
                    DatagramPacket response = new DatagramPacket(responseData, responseData.length);


                    socket.setSoTimeout(2000);

                    socket.send(request);

                    // Aguarda a resposta do Server
                    socket.receive(response);

                    String responseStr = new String(response.getData(), response.getOffset(), response.getLength());

                    Mensagem responseMsg = gson.fromJson(responseStr, Mensagem.class);

                    // Verifica se a resposta do server esta adequada
                    if (responseMsg.requisicao.equals("SEARCH_OK") == false){

                        Exception e = new Exception("Resposta com requisicao invalida! Esperado: SEARCH_OK. Recebido: "+ responseMsg.requisicao);
                        socket.close();
                        throw e;
                    }

                    // Imprime o resultado da busca
                    System.out.println("Peers com arquivo solicitado: " + Arrays.toString(responseMsg.fontesDeArquivos));

                    tentativas= 5;

                    socket.close();


                } catch (Exception e) {

                    // Caso haja qualquer erro, incrementa o inteiro tentativas, e reinicia a busca
                    tentativas++;
                }

                

            }

            if(tentativas <= 3){
                // Apos 3 tentativas de procurar o arquivo sem sucesso, imprime um erro na tela
                System.out.println("Nao foi possivel concluir a procura do arquivo " + arquivoProcurado);
            }

        }

    }

    public static class DownloadController extends Thread{
        // Classe Thread responsavel por receber pedidos de downloads de outro peers
        private ServerSocket listener;

        public DownloadController(ServerSocket ss){

            listener = ss;

        }

        public void run(){

            try {

                while(true){
                    // Aceita a conexao vinda de outro Peer
                    Socket s = listener.accept();

                    // Inicia uma thread para enviar o arquivo para o peer
                    DownloadSendThread sendt = new DownloadSendThread(s);
                    sendt.start();

                    // continua aceitando novas conexoes
                }

                
            } catch (IOException e) {

            }

        }

    }

    public static class DownloadReceiveThread extends Thread{
        // Classe Thread responsavel por fazer o pedido de download e receber o arquivo
        
        private int originport;
        private InetAddress originaddress;
        private InetAddress serverIP;
        private String filename;
        private int udpkeyport;    // usado para chamar o update após baixar um novo arquivo
        
        
        public DownloadReceiveThread(int port, InetAddress addr, InetAddress server, String filen, int udpp){

            originport = port;
            originaddress = addr;
            serverIP = server;
            filename = filen;
            udpkeyport = udpp;

        }

        public void run(){

            Mensagem fileOrder = new Mensagem("DOWNLOAD", filename, 0);
            GsonBuilder builder = new GsonBuilder();
            
            Gson gson = builder.create();

            try{
                // Cria um Socket, um DataInputStream, um PrintWritter e um BufferedReader para realizar a transacao de arquivo
                Socket socket = new Socket(originaddress, originport);
                DataInputStream dataInputS = new DataInputStream(socket.getInputStream());
                PrintWriter pwout = new PrintWriter(socket.getOutputStream());
                BufferedReader brin = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                int bytes = 0;
    
                // Envia uma mensagem com o nome do arquivo solicitado
                String arqNameMsg = gson.toJson(fileOrder)+ "\n";  // verificando se o \n ira resolver

                pwout.print(arqNameMsg);;
                pwout.flush();
    
                // Recebe mensagem com aprovaçao e tamanho do arquivo, ou a negacao
                String approvedStr;
                approvedStr = brin.readLine();

                Mensagem receivedMsg = gson.fromJson(approvedStr, Mensagem.class); 

                // Caso o Peer tenha negado o pedido de download, encerra o processo
                if (!receivedMsg.requisicao.equals("DOWNLOAD_APPROVED")){

                    System.out.println("Peer " + originaddress + ":" + originport + " negou o download!");
                    socket.close();
                    return;

                }

                // Cria um FileOutputStream, para escrever o arquivo recebido na pasta de arquivos
                FileOutputStream fileOutStream = new FileOutputStream(pastaArquivos.getCanonicalPath()+"/"+filename);

                long fileSize = receivedMsg.filelenght;

                byte[] buffer = new byte[512*1024];

                // Recebe o arquivo dividido em chuncks, para nao ter que ler o arquivo inteiro na memoria de uma unica vez
                while (fileSize > 0 && (bytes = dataInputS.read(buffer, 0, (int)Math.min((long)buffer.length, fileSize))) != -1){

                    fileOutStream.write(buffer, 0, bytes);
                    fileOutStream.flush();
                    fileSize -= (long)bytes;
                
                }
                
                // Mensagem de Sucesso de download
                System.out.println("Arquivo " + filename + " baixado com sucesso na pasta " + pastaArquivos.getCanonicalPath());
                
                // Inicia uma thread de update, para atualizar os arquivos que o peer possui no servidor
                Updatethread upt = new Updatethread(serverIP, socket.getLocalAddress(), udpkeyport, filename);

                upt.start();

                fileOutStream.close();

                socket.close();
                
            }
            catch(Exception e) {

                //e.printStackTrace();
                System.out.println("Conexao recusada, alguma informacao incorreta! Favor tentar refazer o download!");

            }

        }

    }


    public static class DownloadSendThread extends Thread{
        // Classe thread responsavel por enviar o arquivo para o Peer que solicitou

        // Necessario receber o socket que aceitou a conexao
        private Socket clientsocket;        
        
        public DownloadSendThread(Socket s){

            clientsocket = s;

        }

        public void run(){

            try{

                // Inicia os streams, writers e reader necessarios para fazer a comunicacao e enviar o arquivo
                DataInputStream dataInputS = new DataInputStream(clientsocket.getInputStream());
                DataOutputStream dataOutS = new DataOutputStream(clientsocket.getOutputStream());
                PrintWriter pwout = new PrintWriter(clientsocket.getOutputStream());
                BufferedReader brin = new BufferedReader(new InputStreamReader(clientsocket.getInputStream()));

                // Recebe a mensagem com o arquivo solicitado
                String fileOrderStr = brin.readLine();

                GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();

                Mensagem receivedMsg = gson.fromJson(fileOrderStr, Mensagem.class); 

                File arqRequested = new File(pastaArquivos.getCanonicalPath()+"/"+receivedMsg.nomeArquivo);

                Random rand = new Random();
                int blockChance = rand.nextInt(10);


                // Bloqueia o download caso o arquivo nao exista, ou se o numero  0<=random<10 for menor que 3: 30% 
                if (!receivedMsg.requisicao.equals("DOWNLOAD") || !arqRequested.exists() || (blockChance < 3)){

                    // Envia a Mensagem de negacao do download, devido a erro de requisicao, inexistencia do arquivo ou chance aleatoria
                    Mensagem downloadFail = new Mensagem("DOWNLOAD_NEGADO");
                    String downFailStr = gson.toJson(downloadFail);

                    pwout.print(downFailStr+"\n");
                    pwout.flush();

                    return;
                }

                FileInputStream fInputS = new FileInputStream(arqRequested);

                // enviar um download aprovado e o tamanho do arquivo
                Mensagem downApproved = new Mensagem("DOWNLOAD_APPROVED", receivedMsg.nomeArquivo, arqRequested.length());

                String downApprovedStr = gson.toJson(downApproved);

                // Envia a mensagem de download aprovado, com o tamanho do arquivo enviado
                pwout.print(downApprovedStr+"\n");
                pwout.flush();


                // Utilizando o buffer para enviar de forma fracionada o arquivo, para nao ter que 
                // armazenar o arquivo inteiro na memoria ram
                byte[] buffer = new byte[512*1024];
                
                int bytes = 0;

                // Le um pedaco do arquivo pelo fileInputStream, e escreve esse pedaco no DataOutStream
                while ((bytes=fInputS.read(buffer)) != -1){

                    dataOutS.write(buffer, 0, bytes);
                    dataOutS.flush();

                }

                dataInputS.close();
                dataOutS.close();
                fInputS.close();

            }

            catch(Exception e){

                //e.printStackTrace();
            }

        }

    }


    public static class Updatethread extends Thread{
        // Classe thread responsavel por atualizar a lista de arquivos que o Peer possui
        
        private InetAddress serverAddress;
        private InetAddress peerAddres;
        private int udpport;
        private String novoArquivo;

        private int tentativas=0;

        public Updatethread(InetAddress server, InetAddress addr, int udpp, String newarq){

            serverAddress = server;
            peerAddres = addr;
            udpport = udpp;
            novoArquivo = newarq;

        }


        public void run(){

            Mensagem updateMsg = new Mensagem("UPDATE", peerAddres, udpport, novoArquivo);

            while(tentativas < 3){

                try{

                    DatagramSocket s = new DatagramSocket();

                    // Cria mensagem de udpate, que contem o nome do novo arquivo a ser adcionado na lista
                    

                    byte[] sendBuffer = new byte[1024];
                    byte[] receiveBuffer = new byte[1024];

                    Gson gson = new Gson();

                    sendBuffer = gson.toJson(updateMsg).getBytes();

                    DatagramPacket updatePacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, 10098);
                    DatagramPacket updateOkPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);                    
                    
                    s.send(updatePacket);
                    
                    // Define um tempo maximo de 2 segundos para receber a confirmacao de update
                    s.setSoTimeout(2000);
                    s.receive(updateOkPacket);
                    

                    String informationReceived = new String(updateOkPacket.getData(), updateOkPacket.getOffset(), updateOkPacket.getLength());

                    Mensagem response = gson.fromJson(informationReceived, Mensagem.class);

                    // Verifica se a mensagem recebida tem a requisicao correta
                    if (response.requisicao.equals("UPDATE_OK")){

                        System.out.println("Lista de arquivos disponiveis atualizada com sucesso!");
                        
                        tentativas += 5;
                        s.close();

                    }
                    else{

                        System.out.println("Update sem resposta, tentando novamente....");
                        tentativas++;

                    }

                }
                catch(Exception e){

                    tentativas++;

                }
            }

            // Caso em 3 tentativas sem sucesso de atualizar, informa o erro na tela
            if (tentativas <= 3){

                System.out.println("Nao foi possivel atualizar a lista de arquivos no servidor, conexao instavel!");

            }

        }

    }


    public static class Leavethread extends Thread{
        // Classe Thread responsavel por desconectar do servidor

        private InetAddress serverAddress=null;
        private Mensagem leaveMessage=null;
        private int tentativas=0;

        public Leavethread(InetAddress server, Mensagem msg){

            serverAddress = server;
            leaveMessage = msg;

        }

        public void run(){

            // Numero de tentativas: 3. 
            while (tentativas < 3){

                try {

                    DatagramSocket socket = new DatagramSocket();

                    byte[] sendBuffer = new byte[1024];
                    byte[] receiveBuffer = new byte[1024];

                    Gson gson = new Gson();

                    sendBuffer = gson.toJson(leaveMessage).getBytes();

                    DatagramPacket leavePacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, 10098);
                    DatagramPacket leaveOkPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                    socket.send(leavePacket);

                    socket.setSoTimeout(2000); // espera até 2 segundos a resposta de leave
                    socket.receive(leaveOkPacket);

                    String informationReceived = new String(leaveOkPacket.getData(), leaveOkPacket.getOffset(), leaveOkPacket.getLength());

                    Mensagem response = gson.fromJson(informationReceived, Mensagem.class);

                    // Verifica se a mensagem possui o tipo correto
                    if (response.requisicao.equals("LEAVE_OK")){

                        System.out.println("Saiu do server com sucesso!");
                        
                        tentativas += 5;
                        socket.close();

                    }
                    else{

                        System.out.println("leave sem resposta, tentando novamente....");
                        tentativas++;

                    }

                    

                } catch (Exception e) {
                    //e.printStackTrace();
                    tentativas++;
                }
            }
            
            // Caso nao seja possivel sair em 3 tentativas, exibe mensagem de erro
            if (tentativas <= 3){

                System.out.println("Nao foi possivel sair do servidor, conexao instavel!");

            }

        }

    }


    public static class Alivethread extends Thread{
        // Classe thread responsavel por manter o peer vivo no servidor

        private DatagramSocket aliveSocket=null;

        public Alivethread(DatagramSocket alivesocket){

            aliveSocket = alivesocket;

        }

        public void run(){

            byte[] sendData = new byte[1024];
            byte[] responseData = new byte[1024];

            Gson gson = new Gson();

            DatagramPacket serverAlivePacket = new DatagramPacket(responseData, responseData.length);
            DatagramPacket peerAliveOK = new DatagramPacket(sendData, sendData.length);


            while (true){

                try {

                    // Aguarda indefinidamente um pacote de alive
                    aliveSocket.receive(serverAlivePacket);

                    String serverText = new String(serverAlivePacket.getData(), serverAlivePacket.getOffset(), serverAlivePacket.getLength());

                    Mensagem serverAliveMsg = gson.fromJson(serverText, Mensagem.class);

                    // Verifica a corretude da mensagem recebida
                    if (serverAliveMsg.requisicao.equals("ALIVE")){

                        // Cria a mensagem de resposta ALIVE_OK
                        Mensagem aliveOK = new Mensagem("ALIVE_OK");

                        sendData = gson.toJson(aliveOK).getBytes();

                        // Define de forma dinamica a porta e o endereco do server, para poder
                        // lidar com os diversos threads e diversos sockets criados pelo server
                        peerAliveOK.setAddress(serverAlivePacket.getAddress());
                        peerAliveOK.setPort(serverAlivePacket.getPort());

                        peerAliveOK.setData(sendData);

                        // Eniva a mensagem ALIVE OK
                        aliveSocket.send(peerAliveOK);

                    }
                }
                catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        }   

    }


    public static class Jointhread extends Thread{     
        // Classe thread responsavel por entrar no servidor
        
        private InetAddress serverAddress=null;
        private Mensagem joinMessage=null;
        private DatagramSocket peerSocket=null; // usado apenas para manter a constancia de porta udp impressa
        private int tentativas=0;

        public Jointhread(DatagramSocket ps, InetAddress server, Mensagem msg){

            serverAddress = server;
            joinMessage = msg;
            peerSocket = ps;

        }

        public void run(){
        
            byte[] sendData = new byte[1024];
            byte[] responseData = new byte[1024];

            Gson gson = new Gson();

            sendData = gson.toJson(joinMessage).getBytes();

            DatagramPacket clientPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 10098);
            DatagramPacket serverResponsePacket = new DatagramPacket(responseData, responseData.length);

            while(tentativas < 3){

                try {

                    DatagramSocket peerSocket2 = new DatagramSocket();

                    peerSocket2.send(clientPacket);

                    peerSocket2.receive(serverResponsePacket);

                    String informationReceived = new String(serverResponsePacket.getData(), serverResponsePacket.getOffset(), serverResponsePacket.getLength());

                    Mensagem response = gson.fromJson(informationReceived, Mensagem.class);

                    // Verifica a corretude da mensagem recebida
                    if (response.requisicao.equals("JOIN_OK")){
                        tentativas = 5;
                        System.out.println("Sou peer " + peerSocket.getLocalAddress() + ":" + peerSocket.getLocalPort() + " com arquivos " + Arrays.toString(joinMessage.arquivos));
                    }
                    else{
                        tentativas++;
                        System.out.println("Resposta invalida!");
                    }

                    peerSocket2.close();


                } catch (IOException e) {
                    tentativas++;
                }
            } 
        
        }

    }

}
