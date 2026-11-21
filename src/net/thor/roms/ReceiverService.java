package net.thor.roms;
import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.security.keystore.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.json.*;

public class ReceiverService extends Service {
 public static volatile String status="Receiver stopped";
 public static volatile boolean running=false;
 public static volatile long lastContact=0,bytesReceived=0,transferSize=0,transferStarted=0;
 public static volatile String desktop="",transferFile="",transferPhase="";
 public static volatile boolean transferring=false;
 static final java.util.concurrent.atomic.AtomicInteger activeRequests=new java.util.concurrent.atomic.AtomicInteger();
 static void transferStatus(String text){status=text;if(text.startsWith("Verifying"))transferPhase="Verifying SD-card copy";else if(text.startsWith("Installed"))transferPhase="Complete — verified on card";else if(text.startsWith("Already installed"))transferPhase="Already installed — verified";else if(text.startsWith("Checking existing"))transferPhase="Checking existing file";}

 static final int PORT=53318;
 volatile boolean shuttingDown=false;
 SSLServerSocket server;final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<Socket>());
 ExecutorService clients;RomStore store;PowerManager.WakeLock wake;
 public static synchronized KeyStore keys()throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias("thor-receiver-v1")){
   KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
   generator.initialize(new KeyGenParameterSpec.Builder("thor-receiver-v1",KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
    .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
    .setCertificateSubject(new X500Principal("CN=Thor ROM Manager"))
    .setCertificateSerialNumber(BigInteger.ONE).setCertificateNotBefore(new Date(0)).setCertificateNotAfter(new Date(4102444800000L)).build());generator.generateKeyPair();
  }return ks;
 }
 public static String token(Context c){android.content.SharedPreferences p=c.getSharedPreferences("roms",0);String t=p.getString("token",null);if(t==null){byte[] b=new byte[32];new SecureRandom().nextBytes(b);t=RomStore.hex(b);p.edit().putString("token",t).commit();}return t;}
 public static JSONObject pairing(Context c)throws Exception {
  String host="";Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){Enumeration<InetAddress> as=ns.nextElement().getInetAddresses();while(as.hasMoreElements()){InetAddress a=as.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())host=a.getHostAddress();}}
  return new JSONObject().put("url","https://"+(host.isEmpty()?"NO_WIFI":host)+":"+PORT).put("token",token(c)).put("fingerprint",RomStore.hex(MessageDigest.getInstance("SHA-256").digest(keys().getCertificate("thor-receiver-v1").getEncoded())));
 }
 public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("transfer","ROM transfers",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"transfer").setContentTitle("Thor ROM Manager").setContentText("Wi-Fi receiver enabled · tap to manage").setSmallIcon(android.R.drawable.stat_sys_download).setContentIntent(pi).build());
  wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"thor:transfer");
  clients=new ThreadPoolExecutor(2,2,0L,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
  new Thread(()->{try{store=new RomStore(this);startServer();}catch(Exception e){status="Cannot start: "+e.getMessage();stopSelf();}},"thor-server").start();
 }
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null&&intent.hasExtra("imports")){
   String[] imports=intent.getStringArrayExtra("imports");String folder=intent.getStringExtra("folder");
   clients.execute(()->{try{long until=System.currentTimeMillis()+10000;while(store==null&&System.currentTimeMillis()<until)Thread.sleep(100);if(store==null)throw new IOException("Receiver could not start");for(String value:imports)importFile(Uri.parse(value),folder);}catch(Exception e){status="Import failed: "+e.getMessage();}});
  }return START_NOT_STICKY;
 }
 void startServer()throws Exception {
  final KeyStore ks=keys();final PrivateKey key=(PrivateKey)ks.getKey("thor-receiver-v1",null);final X509Certificate cert=(X509Certificate)ks.getCertificate("thor-receiver-v1");
  X509KeyManager manager=new X509KeyManager(){public String[] getClientAliases(String k,Principal[] p){return null;}public String chooseClientAlias(String[] k,Principal[] p,Socket s){return null;}public String[] getServerAliases(String k,Principal[] p){return new String[]{"thor"};}public String chooseServerAlias(String k,Principal[] p,Socket s){return "EC".equals(k)?"thor":null;}public X509Certificate[] getCertificateChain(String a){return new X509Certificate[]{cert};}public PrivateKey getPrivateKey(String a){return key;}};
  SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(new KeyManager[]{manager},null,new SecureRandom());
  if(shuttingDown)return;
  server=(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket(PORT);if(shuttingDown){server.close();return;}running=true;status="Ready to receive";
  try{while(!server.isClosed()){Socket socket=server.accept();sockets.add(socket);try{clients.execute(()->handle(socket));}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}}}finally{running=false;}
 }
 static String line(InputStream in)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<8192;i++){int b=in.read();if(b<0)throw new EOFException();if(b==10)return out.toString("UTF-8").replace("\r","");out.write(b);}throw new IOException("Header too long");}
 void reply(OutputStream out,int code,JSONObject body)throws Exception {byte[] data=body.toString().getBytes("UTF-8");out.write(("HTTP/1.1 "+code+" Result\r\nContent-Type: application/json\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));out.write(data);out.flush();}
 void handle(Socket socket){
  boolean held=false,authenticated=false,upload=false;
  try(Socket s=socket){s.setSoTimeout(30000);InputStream in=new BufferedInputStream(s.getInputStream(),65536);OutputStream out=s.getOutputStream();String[] request=line(in).split(" ");if(request.length!=3)throw new IOException("Bad request");Map<String,String> headers=new HashMap<>();int total=0;String row;while(!(row=line(in)).isEmpty()){total+=row.length();if(total>16384)throw new IOException("Headers too large");int split=row.indexOf(':');if(split<0)throw new IOException("Invalid header");String name=row.substring(0,split).trim().toLowerCase(Locale.ROOT);if(headers.containsKey(name))throw new IOException("Duplicate header");headers.put(name,row.substring(split+1).trim());}
   if(!MessageDigest.isEqual(("Bearer "+token(this)).getBytes("UTF-8"),headers.getOrDefault("authorization","").getBytes("UTF-8"))){reply(out,401,new JSONObject().put("error","Pair this device first"));return;}
   authenticated=true;activeRequests.incrementAndGet();lastContact=System.currentTimeMillis();desktop=s.getInetAddress().getHostAddress();
   wake.acquire(6*60*60*1000L);held=true;
   Uri uri=Uri.parse(request[1]);String route=uri.getPath();
   try {
    if(request[0].equals("GET")&&route.equals("/inventory")){reply(out,200,new JSONObject().put("items",store.inventory()).put("connected",true));}
    else if(request[0].equals("GET")&&route.equals("/check")){reply(out,200,store.check(uri.getQueryParameter("path"),uri.getQueryParameter("sha256"),Long.parseLong(uri.getQueryParameter("size"))));}
    else if(request[0].equals("POST")&&route.equals("/upload")){
     if(headers.containsKey("transfer-encoding"))throw new IOException("Use Content-Length");long size=Long.parseLong(headers.getOrDefault("content-length","-1"));if(size<=0)throw new IOException("Invalid file size");String sha=uri.getQueryParameter("sha256");if(sha==null||!sha.matches("[0-9a-f]{64}"))throw new IOException("Checksum required");
     synchronized(ReceiverService.class){if(transferring)throw new IOException("Another transfer is active; retry after it finishes");transferring=true;}
     upload=true;transferFile=uri.getQueryParameter("path");transferSize=size;bytesReceived=0;transferStarted=System.currentTimeMillis();transferPhase="Receiving";
     InputStream counted=new FilterInputStream(in){public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0)bytesReceived+=n;return n;}public int read()throws IOException{int b=in.read();if(b>=0)bytesReceived++;return b;}};
     reply(out,200,store.install(transferFile,counted,size,sha,ReceiverService::transferStatus));
    }else reply(out,404,new JSONObject().put("error","Unknown route"));
   }catch(Exception e){status="Transfer failed: "+e.getMessage();if(upload)transferPhase=status;reply(out,409,new JSONObject().put("error",e.getMessage()==null?"Transfer failed":e.getMessage()));}
  }catch(Exception e){status="Connection ended: "+e.getMessage();if(upload)transferPhase="Interrupted — retry from the computer";}finally{if(upload)transferring=false;if(authenticated){lastContact=System.currentTimeMillis();activeRequests.decrementAndGet();}if(held&&wake.isHeld())wake.release();sockets.remove(socket);}
 }
 void importFile(Uri uri,String folder)throws Exception {
  if(folder==null)throw new IOException("Select a console folder");String name="";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}if(name.isEmpty())throw new IOException("Unknown filename");
  wake.acquire(6*60*60*1000L);
  try(InputStream raw=getContentResolver().openInputStream(uri)){
   if(raw==null)throw new IOException("Cannot open source");
   if(name.toLowerCase(Locale.ROOT).endsWith(".zip")&&!folder.equals("mame")){
    try(ZipInputStream zip=new ZipInputStream(raw)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(entry.isDirectory())continue;String n=entry.getName();if(!n.toLowerCase(Locale.ROOT).matches(".*\\.(nsp|xci|nes|sfc|smc|gb|gbc|gba|nds|3ds|z64|n64|v64|md|gen|gg|sms|a26|pce|bin|cue|chd|iso|rvz|wbfs|wua|gdi|m3u)$"))continue;store.install(folder+"/"+n,zip,-1,null,text->status=text);zip.closeEntry();}}
   }else {
    if(name.toLowerCase(Locale.ROOT).matches(".*\\.(7z|rar|001)$"))throw new IOException("Extract multipart, RAR and 7z on the desktop first");
    store.install(folder+"/"+name,raw,-1,null,text->status=text);
   }
  }finally{if(wake.isHeld())wake.release();}
 }
 public void onTimeout(int startId,int fgsType){status="Receiver timed out. Open the app and start it again.";stopSelf();}
 public void onDestroy(){shuttingDown=true;running=false;lastContact=0;try{if(server!=null)server.close();}catch(Exception ignored){}synchronized(sockets){for(Socket s:sockets)try{s.close();}catch(Exception ignored){}}if(clients!=null)clients.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}
