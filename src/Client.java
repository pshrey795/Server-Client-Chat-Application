import java.io.*;
import java.util.*;
import java.net.*;

public class Client{

    public static String username = "";
    public static String currMessage = "";
    private clientSendThread sendSock = null;
    private clientRecvThread rcvSock = null;
    public static Scanner stdIn = new Scanner(System.in);
    
    //Global function which takes in a response from the server and converts into a response code depending upon the type of response
    //
    //Three digit response code corresponds to an error code according to the protocol.
    //
    //One digit response code mappings are:
    //
    //1: Send socket registered successfully
    //2: Receive socket registered successfully
    //3: Message Sent successfully
    //4: Forwarded message received, data needs to be extracted 
    //
    //Code -1: Response doesn't follow the protocol i.e. the response is either random text or has arrived from a server using different protocol
    public static int parseResponse(String response){
        int responseCode = -1;
        response = response.trim();

        //Converting response message to individual elements
        String[] header = response.split("\n\n");
        
        //Checking if the response message follows the underlying protocol and if it does, then assign proper response codes for identification.
        if(header.length==1){
            String[] keywords = header[0].split(" ",3);
            if(keywords.length==2){
                if(keywords[0].equals("SENT")){
                    if(username.equals(keywords[1])){
                        responseCode = 3;
                    }
                }
            }else if(keywords.length==3){
                if(keywords[0].equals("ERROR")){
                    try{
                        int errorCode = Integer.parseInt(keywords[1]);
                        switch(errorCode){
                            case 100: {if(keywords[2].equals("Malformed username")){responseCode=errorCode;}break;}
                            case 101: {if(keywords[2].equals("No user registered")){responseCode=errorCode;}break;}
                            case 102: {if(keywords[2].equals("Unable to send")){responseCode=errorCode;}break;}
                            case 103: {if(keywords[2].equals("Header incomplete")){responseCode=errorCode;}break;}
                        }
                    }catch(Exception e){
                        System.out.println("Error code is not a number. Exiting....");
                    }
                }else if(keywords[0].equals("REGISTERED")){
                    if(keywords[1].equals("TOSEND")){
                        if(username.equals(keywords[2])){
                            responseCode = 1;
                        }
                    }else if(keywords[1].equals("TORECV")){
                        if(username.equals(keywords[2])){
                            responseCode = 2;
                        }
                    }
                }
            }
        }else if(header.length==2){
            String[] keywords = header[0].split("\n");
            if(keywords.length==2){
                String[] splitString = keywords[0].split(" ");
                if(splitString.length==2){
                    if(splitString[0].equals("FORWARD")){
                        String[] splitString2 = keywords[1].split(" ");
                        if(splitString2.length==2){
                            if(splitString2[0].equals("Content-length:")){
                                try{
                                    int msgLen = Integer.parseInt(splitString2[1]);
                                    currMessage = header[1].trim();
                                    if(msgLen==currMessage.length()){
                                        responseCode = 4;
                                    }else{
                                        currMessage="";
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

    //Managing the registration of sockets before sending messages
    private void manageRegistration(){

        //Registration for sending thread
        while(true){
            int responseCode = this.sendSock.register();
            if(responseCode==1){
                System.out.println("Sending socket registered!");
                break;
            }else if(responseCode==100){
                System.out.println("Please provide a valid username:");
                username = stdIn.nextLine();
            }else{
                System.out.println("Registration failed...Trying again");
            }
        }

        //Registration for receiving thread
        while(true){
            int responseCode = this.rcvSock.register();
            if(responseCode==2){
                System.out.println("Receiving socket registered!");
                break;
            }else if(responseCode==100){
                System.out.println("Please provide a valid username:");
                username = stdIn.nextLine();
            }else{
                System.out.println("Registration failed...Trying again");
            }
        }
    }

    //For starting the client process
    public void startClient(){
        System.out.println("Registering user "+username+" to the server....");
        this.manageRegistration();

        System.out.println("Starting client threads...");
        sendSock.start();
        rcvSock.start();
    }

    //Constructor for creating TCP connections with the server
    public Client(String userName, String serverIP, int serverPort){
        username = userName;
        this.sendSock = new clientSendThread(serverIP,serverPort);
        this.rcvSock = new clientRecvThread(serverIP,serverPort);
    }

    public static void main(String[] args){

        String userName;
        String serverIP = "127.0.0.1";
        int serverPort = 1234;

        if(args.length==3){
            userName = args[0];
            try{
                serverIP = args[1];
                serverPort = Integer.parseInt(args[2]);
            }catch(Exception e){
                System.out.println(e);
                System.exit(1);
            }
            Client msgClient = new Client(userName, serverIP, serverPort);
            msgClient.startClient();
        }else{
            System.out.println("Provide proper number of arguments.");
        }

    }
}

class clientSendThread extends Thread{

    private Socket clientSendSock = null;
    private DataInputStream sockIn = null;
    private DataOutputStream sockOut = null;

    //Send Thread constructor
    public clientSendThread(String serverIP,int serverPort){

        try{
            this.clientSendSock = new Socket(serverIP,serverPort);
            this.sockIn = new DataInputStream(clientSendSock.getInputStream());
            this.sockOut = new DataOutputStream(clientSendSock.getOutputStream());
        }catch(Exception e){
            System.out.println("Error in forming send thread: "+e);
        }

    }

    //Separate register function for sender thread
    public int register(){

        String sendRequest = "REGISTER TOSEND "+Client.username+"\n\n";
        int responseCode = -1;

        try{
            sockOut.writeUTF(sendRequest);
            String sendResponse = sockIn.readUTF();
            responseCode = Client.parseResponse(sendResponse);
    
        }catch(Exception e){
            System.out.println("Error in sending registration: "+e);
        }

        return responseCode;

    }

    @Override
    public void run(){

        System.out.println("Sending Thread started.");

        try{
            this.wait(1000);
        }catch(Exception e){
            System.out.println(e);
        }

        System.out.println("Welcome "+Client.username);
        System.out.println("You can send messages in the format: @[recipient username] message");

        String sendMessage = "";

        while(true){
            
            try{
                String inputString = Client.stdIn.nextLine();
                if(inputString.charAt(0)=='@'){
                    inputString = inputString.substring(1);
                    String[] splitString = inputString.split(" ",2);
                    String msg = splitString[1];
                    sendMessage = "SEND "+splitString[0]+"\nContent-length: "+msg.length()+"\n\n"+msg;
                    sockOut.writeUTF(sendMessage);
                    String sendResponse = sockIn.readUTF();
                    int responseCode = Client.parseResponse(sendResponse);
                    if(responseCode!=3){
                        if(responseCode==102){
                            System.out.println("Recipient doesn't exist. Try again.");
                        }else if(responseCode==103){
                            System.out.println("Incorrect message format. Try again.");
                        }else if(responseCode==101){
                            System.out.println("Please complete registration first.");
                        }else{
                            System.out.println("Message not sent. Please try again.");
                        }
                    }else{
                        System.out.println("Message sent successfully.");
                    }
                }else{
                    System.out.println("Use correct format of message and try again.");
                }
            }catch(Exception e){
                System.out.println("Error in sending messages "+e);
                break;
            }

        }

    }

}

class clientRecvThread extends Thread{
    
    private Socket clientRecvSock = null;
    private DataInputStream sockIn = null;
    private DataOutputStream sockOut = null;

    //Receive Thread Constructor
    public clientRecvThread(String serverIP,int serverPort){

        try{
            this.clientRecvSock = new Socket(serverIP,serverPort);
            this.sockIn = new DataInputStream(clientRecvSock.getInputStream());
            this.sockOut = new DataOutputStream(clientRecvSock.getOutputStream());
        }catch(Exception e){
            System.out.println("Error in forming receive thread: "+e);
        }

    }

    public int register(){

        String rcvRequest = "REGISTER TORECV "+Client.username+"\n\n";
        int responseCode = -1;

        try{
            sockOut.writeUTF(rcvRequest);
            String rcvResponse = sockIn.readUTF();
            responseCode = Client.parseResponse(rcvResponse);
        }catch(Exception e){
            System.out.println("Error in receiving registration: "+e);
        }

        return responseCode;

    }

    private String extractSender(String msg){
        return ((msg.split("\n\n")[0]).split("\n")[0]).split(" ")[1].trim();
    }

    @Override
    public void run(){

        System.out.println("Receiving Thread started.");

        String rcvMessage = "";

        while(true){
            try{
                Client.currMessage = "";
                rcvMessage = sockIn.readUTF();
                int responseCode = Client.parseResponse(rcvMessage);
                String rcvResponse;
                if(responseCode==4){
                    System.out.println("Message Received Successfully");
                    String sender = extractSender(rcvMessage);
                    System.out.println(sender+":"+Client.currMessage);
                    rcvResponse = "RECEIVED "+Client.username+"\n\n";
                }else{
                    rcvResponse = "ERROR 103 Header Incomplete\n\n";
                }
                sockOut.writeUTF(rcvResponse);
            }catch(Exception e){
                System.out.println("Error in receiving messages "+e);
                break;
            }
        }
    }
}