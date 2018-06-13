package com.github.kassak.intellij.advertise;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class AdvertiserService implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(AdvertiserService.class);
  private DatagramSocket mySocket;

  @Override
  public void initComponent() {
    LOG.info("Starting advertiser service...");
    try {
      mySocket = new DatagramSocket(null);
      mySocket.setReuseAddress(true);
      mySocket.bind(new InetSocketAddress("127.255.255.255", 6666));
      new Thread(this::run, "IJ Advertiser Service").start();
    }
    catch (IOException e) {
      LOG.error("Failed to start advertiser service", e);
    }
  }

  @Override
  public void disposeComponent() {
    if (mySocket != null) mySocket.close();
  }

  private void run() {
    LOG.info("Advertiser service started");
    byte[] buf = new byte[6];
    DatagramPacket packet = new DatagramPacket(buf, buf.length);
    try {
      while (!Thread.interrupted()) {
        mySocket.receive(packet);
        handle(packet);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    LOG.info("Advertiser service stopped");
  }

  private void handle(@NotNull DatagramPacket packet) {
    if (isDiscover(packet)) handleDiscover(packet);
  }

  private void handleDiscover(DatagramPacket packet) {
    LOG.info("Handle discover");
    int port = BuiltInServerManager.getInstance().getPort();
    byte[] buf = {'I', 'J', 'A', 'D', 'V', (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF)};
    try (DatagramSocket s = new DatagramSocket()) {
    DatagramPacket response = new DatagramPacket(buf, buf.length, packet.getSocketAddress());
      s.send(response);
    }
    catch (IOException e) {
      LOG.error("Response to discover failed", e);
    }
  }

  private boolean isDiscover(@NotNull DatagramPacket packet) {
    int l = packet.getLength();
    byte[] b = packet.getData();
    int o = packet.getOffset();
    return l == 6
      && b[o] == 'I'
      && b[o + 1] == 'J'
      && b[o + 2] == 'D'
      && b[o + 3] == 'I'
      && b[o + 4] == 'S'
      && b[o + 5] == 'C';
  }
}
