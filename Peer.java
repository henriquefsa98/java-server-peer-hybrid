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

    //public static File fDest = new File("F:/UFABC/2022/2Q/SistemasDistribuidos/Projeto-Sist-Dist/Destino/");

    public static File fOrigin = new File("F:/Teste");
    //public static File fOrigin = new File("F:/UFABC/2022/2Q/SistemasDistribuidos/Projeto-Sist-Dist/Arquivos/");

    public static File fDest = fOrigin;
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        InetAddress peerIPV4 = InetAddress.getByName("127.0.0.1");
        
        DatagramSocket peerSocket = new DatagramSocket(0,peerIPV4);  // udp

        DatagramSocket aliveSocket = new DatagramSocket(0,peerIPV4);

        ServerSocket tcpSocket = new ServerSocket(0, 0, peerIPV4);

        DownloadController clientDC = new DownloadController(tcpSocket);  // tcp

        clientDC.start();

        Alivethread alive = new Alivethread(aliveSocket);    // alive thread
        alive.start();


        System.out.println("address inicial: " + peerSocket.getLocalAddress());

        InetAddress serverAddress = InetAddress.getByName("127.0.0.1");

        // verifica se a pasta de destino de arquivos existe, se nao, cria
        if(!fDest.exists()){
            fDest.mkdirs();
        }
        // verifica se a pasta de origem de arquivos existe, se nao, cria
        if (!fOrigin.exists()){
            fOrigin.mkdirs();
        }

        File[] arquivosF;

        ArrayList<String> nomeArquivos = new ArrayList<String>();

        arquivosF = fOrigin.listFiles();

        for(int i=0; i < arquivosF.length; i++){
            if(arquivosF[i].isFile()){
                nomeArquivos.add(arquivosF[i].getName());
            }
        }

        // For each pathname in the pathnames array
        for (String pathname : nomeArquivos) {
            // Print the names of files and directories
            System.out.println(pathname);
        }


        String comando;    // Entrada do console do usuario

        while(true){

            System.out.println("Digite um comando: ");

            comando = sc.nextLine();

            switch (comando.toUpperCase()){

                case "JOIN":

                    Mensagem joinMessage = new Mensagem("JOIN", peerIPV4, peerSocket.getLocalPort(), aliveSocket.getLocalPort(), tcpSocket.getLocalPort(), nomeArquivos.stream().toArray(String[] :: new));

                    Jointhread jointhread = new Jointhread(peerSocket, serverAddress, joinMessage);

                    System.out.println("JOIN req sent: ");

                    jointhread.start();

                    break;

                case "LEAVE":

                    Mensagem leaveMsg = new Mensagem("LEAVE", peerSocket.getLocalPort());

                    Leavethread leaveThread = new Leavethread(serverAddress, leaveMsg);

                    leaveThread.start();

                    System.out.println("LEAVE");
                    break;

                case "SEARCH":

                    String arqprocurado = new String();

                    System.out.println("Por favor digite o nome do arquivo procurado:");

                    arqprocurado = sc.nextLine();

                    Searchthread st = new Searchthread(arqprocurado, serverAddress);

                    st.start();

                    break;

                case "DOWNLOAD":

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


            }
        }

    }


    public static class Searchthread extends Thread{

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

                    Mensagem clientMsg = new Mensagem("SEARCH", arquivoProcurado, 0);

                    Gson gson = new Gson();

                    byte[] sendData = new byte[1024];
                    byte[] responseData = new byte[1024];

                    sendData = gson.toJson(clientMsg).getBytes();

                    DatagramPacket request = new DatagramPacket(sendData, sendData.length, serverAddress, 10098);
                    DatagramPacket response = new DatagramPacket(responseData, responseData.length);


                    socket.setSoTimeout(500);

                    socket.send(request);

                    socket.receive(response);

                    String responseStr = new String(response.getData(), response.getOffset(), response.getLength());

                    Mensagem responseMsg = gson.fromJson(responseStr, Mensagem.class);

                    if (responseMsg.requisicao.equals("SEARCH_OK") == false){

                        Exception e = new Exception("Resposta com requisicao invalida! Esperado: SEARCH_OK. Recebido: "+ responseMsg.requisicao);
                        socket.close();
                        throw e;
                    }

                    System.out.println("Peers com arquivo solicitado: " + Arrays.toString(responseMsg.fontesDeArquivos));

                    tentativas= 5;

                    socket.close();


                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    //e.printStackTrace();

                    tentativas++;
                }

                if(tentativas >= 3){
                    //System.out.println("Search concluida!");
                }
                else {
                    //System.out.println("Nao foi possivel concluir a Search, conexao instavel!");
                }

            }

        }

    }

    public static class DownloadController extends Thread{

        private ServerSocket listener;

        public DownloadController(ServerSocket ss){

            listener = ss;

        }

        public void run(){

            //byte[] receivedData = new byte[1024];

            try {


                while(true){

                    Socket s = listener.accept();

                    //System.out.println("Requisicao de download recebida do Peer " + s.getLocalAddress());

                    DownloadSendThread sendt = new DownloadSendThread(s);

                    sendt.start();

                }

                
            } catch (IOException e) {

                // TODO Auto-generated catch block
                //e.printStackTrace();

            }

        }

    }

    public static class DownloadReceiveThread extends Thread{


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
            //builder.disableHtmlEscaping();
            //builder.setLenient();
            Gson gson = builder.create();

            try{

                Socket socket = new Socket(originaddress, originport);
                DataInputStream dataInputS = new DataInputStream(socket.getInputStream());
                //DataOutputStream dataOutS = new DataOutputStream(socket.getOutputStream());
                PrintWriter pwout = new PrintWriter(socket.getOutputStream());
                BufferedReader brin = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //byte[] receivedData = new byte[1024];
                int bytes = 0;
    
                // enviar a mensagem com o nome do arquivo solicitado

                //System.out.println("Iniciando processo de download, enviando nome do arquivo...");

                //byte[] sendData = new byte[1024];
                String arqNameMsg = gson.toJson(fileOrder)+ "\n";  // verificando se o \n ira resolver
                //sendData = arqNameMsg.getBytes();

                //System.out.println("sendData tamanho do lado receive: " + sendData.length);
    
                
                //dataOutS.writeChars(arqNameMsg);
                //dataOutS.write(sendData, 0, sendData.length);
                //dataOutS.flush();

                pwout.print(arqNameMsg);;
                pwout.flush();
    
                // receber mensagem com aprovaçao e tamanho do arquivo, ou a negacao
    
                //dataInputS.read(receivedData, 0, receivedData.length);
                String approvedStr;
                approvedStr = brin.readLine();

                //System.out.println("approvedStr :  " + approvedStr);

                //System.out.println("Resposta recebida do peer de origem: "+ approvedStr);

                //String receivedStr = new String(receivedData, 0, receivedData.length);


                Mensagem receivedMsg = gson.fromJson(approvedStr, Mensagem.class); 


                if (receivedMsg.requisicao.equals("DOWNLOAD_APPROVED") == false){

                    System.out.println("Peer " + originaddress + ":" + originport + " negou o download!");
                    socket.close();
                    return;

                }

                //System.out.println("Download aprovado, iniciando recepcao do arquivo....");

                FileOutputStream fileOutStream = new FileOutputStream(fDest.getCanonicalPath()+"/"+filename);

                long fileSize = receivedMsg.filelenght;

                byte[] buffer = new byte[512*1024];

                //System.out.println("tamanho do arquivo: " + fileSize);
                //System.out.println("tamanho do buffer: " + buffer.length);

                while (fileSize > 0 && (bytes = dataInputS.read(buffer, 0, (int)Math.min((long)buffer.length, fileSize))) != -1){

                    fileOutStream.write(buffer, 0, bytes);
                    fileOutStream.flush();
                    fileSize -= (long)bytes;
                    
                             
                    //System.out.println("bytes faltantes: " + fileSize);

                }
                

                //System.out.println("fileSize: " + fileSize + ", bytes: " + bytes);

                System.out.println("Arquivo " + filename + " baixado com sucesso na pasta " + fDest.getCanonicalPath());
    
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

        private Socket clientsocket;        
        
        public DownloadSendThread(Socket s){

            clientsocket = s;

        }

        public void run(){

            try{

                DataInputStream dataInputS = new DataInputStream(clientsocket.getInputStream());
                DataOutputStream dataOutS = new DataOutputStream(clientsocket.getOutputStream());
                PrintWriter pwout = new PrintWriter(clientsocket.getOutputStream());
                BufferedReader brin = new BufferedReader(new InputStreamReader(clientsocket.getInputStream()));

                //byte[] receivedData = new byte[1024];
                

                //System.out.println("Iniciando envio de arquivo, aguardando o nome do arquivo...");

                // Vai ler aqui a Mensagem contendo o nome do arquivo a ser enviado!
                //dataInputS.read(receivedData);
                String fileOrderStr = brin.readLine();
                //System.out.println("fileorder: " + fileOrderStr);

                //System.out.println("Nome do arquivo recebido!");

                //String receivedStr = new String(receivedData, 0, receivedData.length);

                //receivedStr.replace('\n', '\0');
                //System.out.println("receivedStr: " + receivedStr);

                GsonBuilder builder = new GsonBuilder();
                //builder.disableHtmlEscaping();
                //builder.setLenient();
                Gson gson = builder.create();

                Mensagem receivedMsg = gson.fromJson(fileOrderStr, Mensagem.class); 

                File arqRequested = new File(fOrigin.getCanonicalPath()+"/"+receivedMsg.nomeArquivo);

                Random rand = new Random();
                int blockChance = rand.nextInt(10);


                // Bloqueia o download caso o arquivo nao exista, ou se o numero  0<=random<10 for menor que 3: 30% 
                if (!receivedMsg.requisicao.equals("DOWNLOAD") || !arqRequested.exists() || (blockChance < 3)){

                    // parar o codigo e enviar download negado.
                    //System.out.println("O arquivo pedido nao existe na pasta de arquivos do peer!");

                    Mensagem downloadFail = new Mensagem("DOWNLOAD_NEGADO");
                    String downFailStr = gson.toJson(downloadFail);

                    pwout.print(downFailStr+"\n");
                    pwout.flush();

                    return;
                }

                FileInputStream fInputS = new FileInputStream(arqRequested);

                //System.out.println("Download aprovado, enviando tamanho do arquivo...");

                // enviar um download aprovado e o tamanho do arquivo
                Mensagem downApproved = new Mensagem("DOWNLOAD_APPROVED", receivedMsg.nomeArquivo, arqRequested.length());

                //byte[] sendData = new byte[1024];
                String downApprovedStr = gson.toJson(downApproved);

                //sendData = downApprovedStr.getBytes();

                //dataOutS.writeChars(downApprovedStr);
                pwout.print(downApprovedStr+"\n");
                pwout.flush();


                // enviar em pedacinhos, verificar o tamanho ideal de buffer?
                byte[] buffer = new byte[512*1024];
                
                int bytes = 0;

                long start = System.currentTimeMillis();
                long endtime;

                while (/*fileSize > 0*/ (bytes=fInputS.read(buffer)) != -1){

                    
                    dataOutS.write(buffer, 0, bytes);
                    dataOutS.flush();
                    
                    //System.out.println("Bytes enviados: " + bytes);

                }

                endtime = System.currentTimeMillis();
                //dataOutS.flush();
                System.out.println("Arquivo enviado com sucesso em " + (endtime - start) + " milisegundos!");

                dataInputS.close();
                dataOutS.close();
                fInputS.close();


            }

            catch(Exception e){

                e.printStackTrace();
            }


        }

    }

    public static class Updatethread extends Thread{

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

                    byte[] sendBuffer = new byte[1024];
                    byte[] receiveBuffer = new byte[1024];

                    Gson gson = new Gson();

                    sendBuffer = gson.toJson(updateMsg).getBytes();

                    DatagramPacket updatePacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, 10098);
                    DatagramPacket updateOkPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                    

                    s.setSoTimeout(2000);

                    s.send(updatePacket);

                    s.receive(updateOkPacket);

                    String informationReceived = new String(updateOkPacket.getData(), updateOkPacket.getOffset(), updateOkPacket.getLength());

                    Mensagem response = gson.fromJson(informationReceived, Mensagem.class);

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

            if (tentativas <= 3){

                System.out.println("Nao foi possivel atualizar a lista de arquivos no servidor, conexao instavel!");

            }
            


        }


        
    }

    public static class Leavethread extends Thread{

        private InetAddress serverAddress=null;
        private Mensagem leaveMessage=null;
        private int tentativas=0;

        public Leavethread(InetAddress server, Mensagem msg){

            serverAddress = server;
            leaveMessage = msg;

        }

        public void run(){

            while (tentativas < 3){

                try {

                    DatagramSocket socket = new DatagramSocket();

                    byte[] sendBuffer = new byte[1024];
                    byte[] receiveBuffer = new byte[1024];

                    Gson gson = new Gson();

                    sendBuffer = gson.toJson(leaveMessage).getBytes();

                    DatagramPacket leavePacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, 10098);
                    DatagramPacket leaveOkPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                    socket.setSoTimeout(2000); // espera até 2 segundos a resposta de leave

                    socket.send(leavePacket);

                    socket.receive(leaveOkPacket);

                    String informationReceived = new String(leaveOkPacket.getData(), leaveOkPacket.getOffset(), leaveOkPacket.getLength());

                    Mensagem response = gson.fromJson(informationReceived, Mensagem.class);

                    if (response.requisicao.equals("LEAVE_OK")){

                        System.out.println("Saiu do server com sucesso!");
                        // talvez deveria fechar o peer aqui?
                        tentativas += 5;
                        socket.close();

                    }
                    else{

                        System.out.println("leave sem resposta, tentando novamente....");
                        tentativas++;

                    }

                    

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    //e.printStackTrace();
                    tentativas++;
                }
            }
            

            if (tentativas <= 3){

                System.out.println("Nao foi possivel sair do servidor, conexao instavel!");

            }

        }

    }


    public static class Alivethread extends Thread{

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

                    //System.out.println("Aguardando pacote alive...");

                    aliveSocket.receive(serverAlivePacket);

                    //System.out.println("Alive check recebido");

                    String serverText = new String(serverAlivePacket.getData(), serverAlivePacket.getOffset(), serverAlivePacket.getLength());

                    Mensagem serverAliveMsg = gson.fromJson(serverText, Mensagem.class);

                    if (serverAliveMsg.requisicao.equals("ALIVE")){

                        Mensagem aliveOK = new Mensagem("ALIVE_OK");

                        sendData = gson.toJson(aliveOK).getBytes();

                        peerAliveOK.setAddress(serverAlivePacket.getAddress());
                        peerAliveOK.setPort(serverAlivePacket.getPort());

                        peerAliveOK.setData(sendData);

                        aliveSocket.send(peerAliveOK);

                        //System.out.println("Alive OK enviado");

                    }

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }   

    }

    public static class Jointhread extends Thread{     // so aceita se for static, quais sao as consequencias?

        private InetAddress serverAddress=null;
        private Mensagem joinMessage=null;
        private DatagramSocket peerSocket=null;
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

                    // avaliar a necessidade de usar o msm socket da main
                    peerSocket2.send(clientPacket);

                    peerSocket2.receive(serverResponsePacket);

                    String informationReceived = new String(serverResponsePacket.getData(), serverResponsePacket.getOffset(), serverResponsePacket.getLength());

                    Mensagem response = gson.fromJson(informationReceived, Mensagem.class);

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
                    // TODO Auto-generated catch block
                    //e.printStackTrace();
                    //System.out.println("Pedido de JOIN falhou, tentando novamente...");
                    tentativas++;
                }
            }
            
        
        }

    }


    
}
