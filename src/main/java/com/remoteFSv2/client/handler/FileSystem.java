package com.remoteFSv2.client.handler;

import com.remoteFSv2.client.Config;
import com.remoteFSv2.utils.Constants;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;

public class FileSystem implements Closeable
{

    private JSONObject request = new JSONObject();

    private final String username;

    private final Socket socket;

    public final BufferedReader reader;

    public final PrintWriter writer;

    public FileSystem(String username, Socket socket) throws IOException
    {
        this.username = username;

        this.socket = socket;

        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        this.writer = new PrintWriter(socket.getOutputStream(), true);
    }

    public void listFiles() throws IOException
    {

        request.clear();

        request.put("username", username);
        request.put("command", Constants.LIST);

        writer.println(request.toString());
        var response = reader.readLine();

        var resJSON = new JSONObject(response);

        if(resJSON.getInt("status") == 0)
        {
            System.out.println(resJSON.get("files"));
        }
        else
        {
            System.out.println(Constants.CLIENT + Constants.EMPTY_DIRECTORY);
        }


    }

    public void reqDownloadFile(String fileChoice) throws IOException
    {
        request.clear();

        request.put("username", username);
        request.put("command", Constants.DOWNLOAD);

        writer.println(request.toString());
        var response = reader.readLine();

        var resJSON = new JSONObject(response);

        if(response.equals("null"))
        {
            throw new IOException();
        }
        var command = response.split(" ", 2)[0]; // "START_RECEIVING" command

        if(command.equals("START_RECEIVING"))
        {
            var argument = response.split(" ", 2)[1]; // FILE-NAME

            if(receiveFileFromServer(argument))
            {
                System.out.println("[Client] File downloaded successfully!");

                reader.readLine();

            }
            else
            {
                System.out.println("[Client] Error! File not received properly!");
            }
        }
        else
        {
            System.out.println("[Client] File not found on server!");
        }


    }

    public boolean receiveFileFromServer(String fileName) throws IOException
    {
        request.clear();

        request.put("username", username);
        request.put("command", Constants.START_SENDING);
        request.put("fileName", fileName.trim());

        writer.println(request.toString());


        var bytes = 0;

        var dataInputStream = new DataInputStream(socket.getInputStream());

        var fileOutputStream = new FileOutputStream(Config.ROOT_DIR_CLIENT + fileName);

        // read file size
        var size = dataInputStream.readLong();

        var buffer = new byte[8192]; // 8KB

        while(size > 0 && (bytes = dataInputStream.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1)
        {
            // Here we write the file using write method
            fileOutputStream.write(buffer, 0, bytes);

            size -= bytes; // read upto file size
        }

        fileOutputStream.close();

        return true;

    }


    public boolean uploadFile(String localPath)
    {
        var fileDirectories = localPath.trim().split("/");

        var fileName = fileDirectories[fileDirectories.length - 1];

        if(Files.exists(Paths.get(localPath)) && fileName.contains("."))
        {
            try
            {
                writer.println("UPLOAD " + fileName);

                var file = new File(localPath);

                var dataOutputStream = new DataOutputStream(socket.getOutputStream());

                FileInputStream fileInputStream = new FileInputStream(file);

                // Here we send the File to Server
                dataOutputStream.writeLong(file.length());

                var bytes = 0;

                // Here we break file into 8KB chunks
                var buffer = new byte[8192];

                while((bytes = fileInputStream.read(buffer)) != -1)
                {
                    // Send the file to Server Socket
                    dataOutputStream.write(buffer, 0, bytes);

                    dataOutputStream.flush();
                }

                // close the file here
                fileInputStream.close();

                reader.readLine();

                return true;

            } catch(NullPointerException npe)
            {
                System.out.println("[Client] Server is down!");

                return false;

            } catch(FileNotFoundException e)
            {
                System.out.println("[Client] File not found!");

                return false;

            } catch(IOException io)
            {
                System.out.println("[Client] Data input/output stream error...\nError: " + io.getMessage());

                return false;
            }

        }
        else
        {
            System.out.println("[Client] Incorrect file path!");

            return false;
        }

    }


    public void deleteFile(String fileName)
    {
        try
        {
            request.clear();

            request.put("username", username);
            request.put("command", Constants.DELETE);
            request.put("fileName",fileName);

            writer.println(request.toString());
            var response = reader.readLine();

            var resJSON = new JSONObject(response);

            if(response.equals("null"))
            {
                throw new IOException();
            }

            System.out.println(response);

        } catch(IOException | NullPointerException e)
        {
            System.out.println("[Client] Server is down!");
        }
    }

    public void close() throws IOException
    {
        reader.close(); // Close input stream

        writer.close(); // Close output stream

        socket.close(); // Close socket connection
    }
}
