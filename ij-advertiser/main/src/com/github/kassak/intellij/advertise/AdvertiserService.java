package com.github.kassak.intellij.advertise;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.EditCustomVmOptionsAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class AdvertiserService implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(AdvertiserService.class);
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Advertiser Service");
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
      ProjectManager manager = ProjectManager.getInstance();
      Project project = ContainerUtil.find(manager.getOpenProjects(), p -> !p.isDefault() && p.isInitialized() && p.isOpen());
      if (project != null) {
        notifySolution(project, e);
      }
      else {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
          @Override
          public void projectOpened(Project project) {
            connection.disconnect();
            notifySolution(project, e);
          }
        });
      }
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

  private void notifySolution(Project p, Exception e) {
    LOG.warn("notify");
    boolean known = "true".equals(System.getProperty("java.net.preferIPv4Stack"));
    NOTIFICATION_GROUP.createNotification(
      "Failed to start advertiser",
      e.getMessage() + "\n" +
        (known
          ? "Please remove -Djava.net.preferIPv4Stack=true from <a href=\"opt\">VM options</a>." +
          "Sorry for inconvenience :("
          : "Don't know what to do :("),
      NotificationType.ERROR,
      (notification, event) -> {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ActionUtil.performActionDumbAware(
            new EditCustomVmOptionsAction(),
            AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, DataManager.getInstance().getDataContext()));
        }
      }
    ).notify(p);
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
