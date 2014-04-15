/*
 * This file is part of the dSploit.
 *
 * Copyleft of Simone Margaritelli aka evilsocket <evilsocket@gmail.com>
 * 			   Massimo Dragano	aka tux_mind <massimo.dragano@gmail.com>
 *
 * dSploit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dSploit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with dSploit.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.evilsocket.dsploit.net.metasploit;

import java.io.IOException;
import java.net.MalformedURLException;

import it.evilsocket.dsploit.R;
import it.evilsocket.dsploit.core.Shell;
import it.evilsocket.dsploit.core.System;
import it.evilsocket.dsploit.core.Logger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Date;
import java.util.concurrent.TimeoutException;

/* NOTES
 * i have choosen to NOT use SSL for one reason: without SSL we don't need /dev/random in gentoo chroot,
 * so we don't have to "if mountpoint /data/gentoo/dev; mount -o bind /dev /data/gentoo/dev; fi".
 * security flaws: a local sniffer can grep the msfrpcd username/password.
*/

public class RPCServer extends Thread
{
  public static final String 	TOAST 			= "RPCServer.action.TOAST";
  public static final String 	ERROR       = "RPCServer.action.ERROR";
  public static final String  STRINGID    = "RPCServer.data.STRINGID";
  private final static long 	TIMEOUT			= 540000; // 4 minutes
  private final static int    DELAY 			= 5000; // poll every 5 secs

  private Context         mContext	 		  = null;
  private boolean         mRunning	 		  = false;
  private boolean         mRemote 	 		  = false;
  private boolean         msfSsl    	 		= false;
  private String          msfHost,
                          msfUser,
                          msfPassword,
                          msfRoot;
  private int             msfPort;
  private long            mTimeout = 0;

  public RPCServer(Context context) {
    super("RPCServer");
    mContext   = context;
  }

  public static boolean exists() {
    return (new java.io.File(System.getGentooPath() + "start_msfrpcd.sh")).exists();
  }

  public static boolean isInternal() {
    return System.getNetwork().isInternal(System.getSettings().getString("MSF_RPC_HOST", "127.0.0.1"));
  }

  public static boolean isLocal() {
    return System.getSettings().getString("MSF_RPC_HOST", "127.0.0.1").equals("127.0.0.1");
  }

  private void sendDaemonNotification(String action, int message)
  {
    Intent i = new Intent(action);
    i.putExtra(STRINGID, message);
    mContext.sendBroadcast(i);
  }

  public boolean isRunning() {
    return mRunning;
  }

  private boolean connect_to_running_server() throws RuntimeException, IOException, InterruptedException {
    boolean ret = false;

    if(mRemote || Shell.exec("pidof msfrpcd")==0)
    {
      try
      {
        System.setMsfRpc(new RPCClient(msfHost,msfUser,msfPassword,msfPort,msfSsl));
        ret = true;
      }
      catch ( MalformedURLException mue)
      {
        System.errorLogging(mue);
        throw new RuntimeException();
      }
      catch ( IOException ioe)
      {
        Logger.debug(ioe.getMessage());
        if(mRemote)
          throw new RuntimeException();
      }
      catch ( RPCClient.MSFException me)
      {
        System.errorLogging(me);
        throw new RuntimeException();
      }
      finally {
        if(!ret && !mRemote)
          Shell.exec("killall msfrpcd");
      }
    }
    return ret;
  }

  /* WARNING: this method will hang forever if msfrpcd start successfully,
   * use it only for report server crashes.
   * NOTE: it can be useful if we decide to own the msfrpcd process
  */
  private void start_daemon_fg() {
    class debug_receiver implements Shell.OutputReceiver {
      @Override
      public void onStart(String command) {
        Logger.debug("running \""+command+"\"");
      }

      @Override
      public void onNewLine(String line) {
        Logger.debug(line);
      }

      @Override
      public void onEnd(int exitCode) {
        Logger.debug("exitValue="+exitCode);
      }
    }

    try
    {
      if(Shell.exec( "chroot '" + msfRoot + "' /start_msfrpcd.sh -P '" + msfPassword + "' -U '" + msfUser + "' -p " + msfPort + " -a 127.0.0.1 -n -S -t Msg -f", new debug_receiver()) != 0) {
        Logger.error("chroot failed");
      }
    }
    catch ( Exception e)
    {
      System.errorLogging(e);
    }
  }

  private void start_daemon() throws RuntimeException, IOException, InterruptedException, TimeoutException {
    Thread res;
    Shell.StreamGobbler chroot;
    long time;

    res = Shell.async( "chroot '" + msfRoot + "' /start_msfrpcd.sh -P '" + msfPassword + "' -U '" + msfUser + "' -p " + msfPort + " -a 127.0.0.1 -n -S -t Msg");
    if(!(res instanceof Shell.StreamGobbler))
      throw new IOException("cannot run shell commands");
    chroot = (Shell.StreamGobbler) res;
    chroot.setName("chroot");
    chroot.start();
    try {
      do {
        Thread.sleep(100);
        time=(new Date()).getTime();
      } while(chroot.isAlive() && time < mTimeout);
    } catch (InterruptedException e) {
      //ensure to kill the chroot thread
      chroot.interrupt();
      throw e;
    }
    if(time >= mTimeout) {
      chroot.interrupt();
      throw new TimeoutException("chrooting timed out");
    }
    chroot.join();
    if(chroot.exitValue !=0) {
      Logger.error("chroot returned "+chroot.exitValue);
      throw new RuntimeException("chroot failed");
    }
  }

  private void wait_for_connection() throws RuntimeException, IOException, InterruptedException, TimeoutException {

    do {
      if(Shell.exec("pidof msfrpcd")!=0)
      {
        // OMG, server crashed!
        // start server in foreground and log errors.
        sendDaemonNotification(ERROR,R.string.error_rcpd_fatal);
        start_daemon_fg();
        throw new InterruptedException("exiting due to server crash");
      }
      try
      {
        Thread.sleep(DELAY);
        System.setMsfRpc(new RPCClient(msfHost,msfUser,msfPassword,msfPort,msfSsl));
        return;
      }
      catch ( MalformedURLException mue)
      {
        System.errorLogging(mue);
        throw new RuntimeException();
      }
      catch ( IOException ioe)
      {
        // cannot connect now...
      }
      catch ( RPCClient.MSFException me )
      {
        System.errorLogging(me);
        throw new RuntimeException();
      }
    } while(new Date().getTime() < mTimeout);
    Logger.debug("MSF RPC Server timed out");
    throw new TimeoutException();
  }

  @Override
  public void run( ) {
    mTimeout = new Date().getTime() + TIMEOUT;
    Logger.debug("RPCServer started");

    mRunning = true;

    SharedPreferences prefs = System.getSettings();

    msfHost     = prefs.getString("MSF_RPC_HOST", "127.0.0.1");
    msfUser     = prefs.getString("MSF_RPC_USER", "msf");
    msfPassword = prefs.getString("MSF_RPC_PSWD", "pswd");
    msfRoot     = prefs.getString("GENTOO_ROOT", System.getDefaultGentooPath());
    msfPort     = prefs.getInt("MSF_RPC_PORT", 55553);
    msfSsl      = prefs.getBoolean("MSF_RPC_SSL", false);

    mRemote     = !msfHost.equals("127.0.0.1");

    try
    {
      if(!connect_to_running_server()) {
        sendDaemonNotification(TOAST,R.string.rpcd_starting);
        start_daemon();
        wait_for_connection();
        sendDaemonNotification(TOAST,R.string.rpcd_started);
        Logger.debug("connected to new MSF RPC Server");
      } else {
        sendDaemonNotification(TOAST, R.string.connected_msf);
        Logger.debug("connected to running MSF RPC Server");
      }
    } catch ( IOException ioe ) {
      Logger.error(ioe.getMessage());
      sendDaemonNotification(ERROR,R.string.error_rpcd_shell);
    } catch ( InterruptedException e ) {
      if(e.getMessage()!=null)
        Logger.debug(e.getMessage());
      else
        System.errorLogging(e);
    } catch ( RuntimeException e ) {
      sendDaemonNotification(ERROR,R.string.error_rpcd_inval);
    } catch (TimeoutException e) {
      sendDaemonNotification(TOAST,R.string.rpcd_timedout);
    }
    mRunning = false;
  }

  public void exit() {
    if(this.isAlive())
      this.interrupt();
  }
}