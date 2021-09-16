import java.io.*;
import java.util.*;
import java.net.*;

public class Server {
    
    private ServerSocket serverSocket = null;
    public static HashMap<String,RecvSocket> rcvSockets = new HashMap<>();

    //Global function which takes in a response from the server and converts into a response code depending upon the type of response
    //
    //Three digit response code corresponds to an error code according to the protocol.
    //
    //One digit response code mappings are:
    //
    //1: Send socket registered successfully
    //2: Receive socket registered successfully
    //3: Message Received successfully
    //4: Send message received, data needs to be extracted 
    //
    //Code -1: Response doesn't follow the protocol i.e. the response is either random text or has arrived from a server using different protocol
    public static int parseResponse(String response,String userName){
        int responseCode = -1;
        response = response.trim();

        String[] header = response.split("\n\n");

        if(header.length==1){
            String[] keywords = header[0].split(" ",3);
            if(keywords.length==2){
                if(keywords[0].equals("RECEIVED")){
                    if(userName.equals(keywords[1])){
                        responseCode = 3;
                    }
                }
            }else if(keywords.length==3){
                if(keywords[0].equals("ERROR")){
                    try{
                        int errorCode = Integer.parseInt(keywords[1]);
                        if(errorCode==103){
                            if(keywords[2].equals("Header incomplete")){
                                responseCode = 103;
                            }
                        }
                    }catch(Exception e){
                        System.out.println("Error code is not a number. The client will be ignored.");
                    }
                }else if(keywords[0].equals("REGISTER")){
                    if(keywords[1].equals("TOSEND")){
                        responseCode = 1;
                    }else if(keywords[1].equals("TORECV")){
                        responseCode = 2;
                    }
                }
            }
        }else if(header.length==2){
            String[] keywords = header[0].split("\n");
            if(keywords.length==2){
                String[] splitString = keywords[0].split(" ");
                if(splitString.length==2){
                    if(splitString[0].equals("SEND")){
                        String[] splitString2 = keywords[1].split(" ");
                        if(splitString2.length==2){
                            if(splitString2[0].equals("Content-length:")){
                                try{
                                    int msgLen = Integer.parseInt(splitString2[1]);
                                    String currMessage = header[1].trim();
                                    if(msgLen==currMessage.length()){
                                        responseCode = 4;
                                    }
                                }catch(Exception e){
                                    System.out.println("Message Length should be an integer.");
                                }
                            }
                        }
                    }
                }
            }
        }

        return responseCode;
    }

    private boolean isValid(String uname){
        for(int i=0;i<uname.length();i++){
            int idx = (int)uname.charAt(i);
            if((idx>=97 && idx<=122) || (idx>=65 && idx<=90) || (idx>=48 && idx<=57)){
                continue;
            }else{
                return false;
            }
        }
        return true;
    }

    public void serverStart(){
        while(true){
            try{
                Socket clientSocket = serverSocket.accept();
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                System.out.println("New client connected: "+clientSocket);

                //Managing Registration of incoming requests
                String request = dis.readUTF();
                int responseCode = parseResponse(request, "");
                System.out.println(responseCode);
                String response = "";
                if(responseCode==1){
                    String newUser = (request.split("\n\n")[0]).split(" ",3)[2];
                    if(isValid(newUser)){
                        response = "REGISTERED TOSEND "+newUser+"\n\n";
                        ClientHandler newClient = new ClientHandler(newUser,clientSocket,dis,dos);
                        newClient.start();
                    }else{
                        response = "ERROR 100 Malformed username\n\n";
                    }
                }else if(responseCode==2){
                    String newUser = (request.split("\n\n")[0]).split(" ",3)[2];
                    if(isValid(newUser)){
                        response = "REGISTERED TORECV "+newUser+"\n\n";
                        RecvSocket newClient = new RecvSocket(clientSocket,dis,dos);
                        rcvSockets.put(newUser, newClient);
                    }else{
                        response = "ERROR 100 Malformed username\n\n";
                    }
                }else{
                    response = "ERROR 101 No user registered\n\n";
                }
                dos.writeUTF(response);
            }catch(Exception e){
                System.out.println("Error in accepting client: "+e);
            }

        }
    }

    public Server(int port) {
        
        try{
            serverSocket = new ServerSocket(port);
        }catch(Exception e){
            System.out.println("Error in initialising server socket: "+e);
        }

    }

    public static void main(String[] args){

        int PORT_NO = Integer.valueOf(args[0]);
        Server msgServer = new Server(PORT_NO);
        msgServer.serverStart();

    }
}

class ClientHandler extends Thread{

    private String username;
    private Socket clientSocket = null;
    private DataInputStream sockIn = null;
    private DataOutputStream sockOut = null;

    public ClientHandler(String userName, Socket clientSocket, DataInputStream dis, DataOutputStream dos){

        try{
            this.username = userName;
            this.clientSocket = clientSocket;
            this.sockIn = dis;
            this.sockOut = dos;
        }catch(Exception e){
            System.out.println("Error in initialising client sending thread:"+e);
        }

    }

    public String extractRecipient(String msg){
        return ((msg.split("\n\n")[0]).split("\n")[0]).split(" ")[1].trim();        
    }

    @Override
    public void run(){   
        System.out.println("Welcome "+username);

        while(true){  
            try{
                String message = sockIn.readUTF();
                int responseCode = Server.parseResponse(message, username);
                String response = "";
                if(responseCode==4){
                    String recipient = extractRecipient(message);
                    if(Server.rcvSockets.containsKey(recipient)){
                        int len = (message.split("\n\n")[0]).split("\n")[0].length();
                        message = message.substring(len);
                        message = "FORWARD "+this.username+message;
                        Server.rcvSockets.get(recipient).sockOut.writeUTF(message);
                        response = Server.rcvSockets.get(recipient).sockIn.readUTF();
                        responseCode = Server.parseResponse(response, recipient);
                        if(responseCode == 3){
                            response = "SENT "+username+"\n\n";
                        }else{
                            response = "ERROR 102 Unable to send\n\n";
                        }
                        sockOut.writeUTF(response);
                    }else{
                        response = "ERROR 102 Unable to send\n\n";
                        sockOut.writeUTF(response);
                    }
                }else{
                    response = "ERROR 103 Header Incomplete\n\n";
                    sockOut.writeUTF(response);
                }
            }catch(Exception e){
                System.out.println("Error in client thread "+e);
                break;
            }

        }
    }

}

class RecvSocket{
    public Socket recvSocket = null;
    public DataInputStream sockIn = null;
    public DataOutputStream sockOut = null;

    public RecvSocket(Socket recvSocket, DataInputStream dis, DataOutputStream dos){
        try{
            this.recvSocket = recvSocket;
            this.sockIn = dis;
            this.sockOut = dos;
        }catch(Exception e){
            System.out.println("Error in initialising client receive socket"+e);
        }
    }
}
