import java.net.InetAddress;
import java.util.Arrays;

public class Mensagem {

    public String requisicao;

    public InetAddress ipv4client;

    public int udpport;

    public int udpAlivePort;

    public int tcpport;
    
    public long filelenght;

    public String nomeArquivo;

    public String[] arquivos;

    public String[] fontesDeArquivos;


    // Mensagem de JOIN
    public Mensagem(String req, InetAddress ipv4addr, int udp, int aliveport, int tcp, String[] arqs){

        requisicao = req;
        ipv4client = ipv4addr;
        udpport = udp;
        tcpport = tcp;
        arquivos = arqs;
        udpAlivePort = aliveport;

    }

    // Mensagem para ALIVE
    public Mensagem(String req){

        requisicao = req;

    }

    // Mensagem para LEAVE
    public Mensagem(String req, int udpp){

        requisicao = req;
        udpport = udpp;

    }

    // Mensagem para SEARCH - resposta
    public Mensagem(String req, String[] listapeers){

        requisicao = req;
        fontesDeArquivos = listapeers;

    }

    // Mensagem para SEARCH - pedido e para Download - pedido
    public Mensagem(String req, String arqn, long filel){

        requisicao = req;
        nomeArquivo = arqn;
        filelenght = filel;
    
    }



    public void printMensagem(){

        System.out.println("Mensagem{ Requisicao: " + requisicao + ", IP:" + ipv4client + ", UDP: " + udpport + ", AlivePort: " + udpAlivePort + ", TCP: " + tcpport + ", Arquivos: " + Arrays.toString(arquivos) +" }");


    }
    
}
