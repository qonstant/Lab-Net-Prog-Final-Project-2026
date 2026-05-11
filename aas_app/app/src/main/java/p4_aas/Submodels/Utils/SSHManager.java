package p4_aas.Submodels.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * To Manage SSH Connections to each Host/Controller in Network Configuration.
 * 
 * Each Network Node is configured to:
 *  - Allow root access
 *  - Root password at: 111111
 *  - Port: 22
 * 
 * So each connection is very similar and simpler to manage.
 */
public class SSHManager {
    private final String SSH_PSW = "111111";
    private final String SSH_USER = "root";
    private final Integer SSH_PORT = 22;
    private final Integer TIMEOUT =  30000;

    /**
     * Channel is of type Shell, so multiplòe commands can be executed on it
     * @param command to be executed on given Channel
     * @param ch Channel on which to execute given Command
     */
    public void executeCommandOnShell(String command, Channel ch) {
        PrintStream ps;
        try {
            ps = new PrintStream(ch.getOutputStream());
            ps.println(command);
            ps.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Connection is opened on type "exec", one command is executed and then connection closes.
     * @param command to be executed one single time on given host
     * @param host IP on which to execute given command
     * @return Result of given executed command.
     */
    public String executeSingleCommand(String command, String host) {
        ChannelExec ch = (ChannelExec) this.channelInit("exec", host);
        if (ch == null) {
            return "Unable to open SSH channel to " + host;
        }
        ch.setCommand(command);
        return this.readChannelOutput(ch);
    }

    /**
     * 
     * @param channel from which to read command output
     * @return command output
     */
    private String readChannelOutput(ChannelExec channel) {
        StringBuilder sb = new StringBuilder();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setErrStream(err);

        try {            
            BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            channel.connect();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException | JSchException e) {
            sb.append(e.getMessage()).append("\n");
        } finally {
            try {
                if (err.size() > 0) {
                    sb.append(err.toString());
                }
                if (channel.getSession() != null) {
                    channel.getSession().disconnect();
                }
            } catch (JSchException e) {
                sb.append(e.getMessage()).append("\n");
            }
        }
        
        return sb.toString();   
    }

    /**
     * @param type of JSCH channel to be opened (Shell or Exec)
     * @param sshHost to connect to
     * @return created and started Channel on which to execute commands.
     */
    public Channel channelInit(String type, String sshHost) {
        Channel channel = null;
        try {
            Session session = this.setupSshSession(sshHost);
            session.connect(TIMEOUT);
            channel = session.openChannel(type);
        } catch (JSchException e) {
            e.printStackTrace();
        }

        return channel;
    }

    /**
     * 
     * @param SSH_HOST to connect to
     * @return created and configured SSH session
     */
    private Session setupSshSession(String SSH_HOST) {
        Session session = null;
        try {
            session = new JSch().getSession(SSH_USER, SSH_HOST, SSH_PORT);
            session.setPassword(SSH_PSW);
            session.setConfig("StrictHostKeyChecking", "no"); // disable check for RSA key
        } catch (JSchException e) {
            e.printStackTrace();      
        }
        
        return session;
    }
}
