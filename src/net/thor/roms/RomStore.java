package net.thor.roms;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** All writes stay under the user-selected SAF tree. Partial files are never installed. */
public class RomStore {
 final Context context; final ContentResolver resolver; final Uri tree,root;
 public interface Progress {void update(String text);}
 public RomStore(Context c)throws Exception {
  context=c;resolver=c.getContentResolver();String value=c.getSharedPreferences("roms",0).getString("tree",null);
  if(value==null)throw new IOException("Choose the SD card ROMs folder first.");
  tree=Uri.parse(value);root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
  // Verify access instead of reporting an empty library for a removed card.
  children(root);
 }
 public static String clean(String path)throws IOException {
  if(path==null||path.length()>1024||path.startsWith("/")||path.contains("\\"))throw new IOException("Invalid relative path");
  for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.startsWith(".")||part.matches(".*[\\x00-\\x1f].*"))throw new IOException("Unsafe relative path");
  if(!path.contains("/"))throw new IOException("Choose a console folder");return path;
 }
 static class Entry {Uri uri;String name,mime;long size;Entry(Uri u,String n,String m,long s){uri=u;name=n;mime=m;size=s;}boolean dir(){return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);}}
 List<Entry> children(Uri directory)throws Exception {
  Uri query=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(directory));List<Entry> result=new ArrayList<>();
  try(Cursor c=resolver.query(query,new String[]{"document_id","_display_name","mime_type","_size"},null,null,null)) {
   if(c==null)throw new IOException("Cannot read ROMs folder. Reconnect the card or choose the folder again.");
   while(c.moveToNext())result.add(new Entry(DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)),c.getString(1),c.getString(2),c.getLong(3)));
  }return result;
 }
 Entry find(Uri dir,String name)throws Exception {for(Entry e:children(dir))if(e.name.equalsIgnoreCase(name))return e;return null;}
 Uri parent(String path,boolean create)throws Exception {
  Uri dir=root;String[] parts=path.split("/");
  for(int i=0;i<parts.length-1;i++){Entry e=find(dir,parts[i]);if(e==null){if(!create)return null;dir=DocumentsContract.createDocument(resolver,dir,DocumentsContract.Document.MIME_TYPE_DIR,parts[i]);if(dir==null)throw new IOException("Cannot create folder");}else {if(!e.dir())throw new IOException("Folder name belongs to a file");dir=e.uri;}}
  return dir;
 }
 public static String hex(byte[] data){StringBuilder b=new StringBuilder();for(byte v:data)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}
 String hash(Uri uri)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new IOException("Cannot open file");byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return hex(digest.digest());}
 public JSONObject check(String path,String sha,long size)throws Exception {
  clean(path);Uri dir=parent(path,false);Entry e=dir==null?null:find(dir,path.substring(path.lastIndexOf('/')+1));
  boolean exists=e!=null;boolean matches=exists&&!e.dir()&&e.size==size&&hash(e.uri).equals(sha);
  return new JSONObject().put("exists",exists).put("matches",matches);
 }
 public synchronized JSONObject install(String path,InputStream input,long size,String expected,Progress progress)throws Exception {
  clean(path);if(size>128L*1024*1024*1024)throw new IOException("File exceeds 128 GiB limit");
  Uri dir=parent(path,true);String name=path.substring(path.lastIndexOf('/')+1);Entry existing=find(dir,name);
  if(existing!=null){
   if(existing.dir())throw new IOException("Destination is a directory: "+path);
   progress.update("Checking existing "+path);MessageDigest h=MessageDigest.getInstance("SHA-256");long count=0;
   try(InputStream old=resolver.openInputStream(existing.uri)){
    if(old==null)throw new IOException("Cannot read existing file");byte[] a=new byte[1024*1024],b=new byte[1024*1024];
    while(size<0||count<size){int n=input.read(a,0,(int)(size<0?a.length:Math.min(a.length,size-count)));if(n<0)break;int got=0;while(got<n){int part=old.read(b,got,n-got);if(part<0)throw new IOException("Existing file differs: "+path);got+=part;}for(int i=0;i<n;i++)if(a[i]!=b[i])throw new IOException("Existing file differs: "+path);h.update(a,0,n);count+=n;if(count>128L*1024*1024*1024)throw new IOException("File exceeds limit");}
    if(old.read()!=-1||(size>=0&&count!=size))throw new IOException("Existing file differs: "+path);
   }
   String sha=hex(h.digest());if(expected!=null&&!expected.equals(sha))throw new IOException("Checksum mismatch");
   progress.update("Already installed "+path);return new JSONObject().put("path",path).put("bytes",count).put("sha256",sha).put("verified",true).put("skipped",true);
  }
  Uri temporary=DocumentsContract.createDocument(resolver,dir,"application/octet-stream",".thor-"+UUID.randomUUID()+".part");
  if(temporary==null)throw new IOException("Cannot create staging file");
  try {
   MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0,last=0;
   try(OutputStream out=resolver.openOutputStream(temporary,"wt")) {
    if(out==null)throw new IOException("Cannot open staging file");byte[] b=new byte[1024*1024];
    while(size<0||count<size){if(Thread.currentThread().isInterrupted())throw new IOException("Stopped");int n=input.read(b,0,(int)(size<0?b.length:Math.min(b.length,size-count)));if(n<0)break;out.write(b,0,n);digest.update(b,0,n);count+=n;if(count>128L*1024*1024*1024)throw new IOException("File exceeds limit");long now=System.currentTimeMillis();if(now-last>1000){progress.update("Receiving "+path+" · "+(count/1048576)+" MiB" );last=now;}}
    out.flush();
   }
   if(size>=0&&count!=size)throw new IOException("Transfer interrupted; retry this file");
   String sha=hex(digest.digest());if(expected!=null&&!expected.equals(sha))throw new IOException("Source checksum mismatch");
   progress.update("Verifying "+path);
   if(!hash(temporary).equals(sha))throw new IOException("Card readback checksum mismatch");
   if(find(dir,name)!=null)throw new IOException("Destination appeared during transfer; not overwritten");
   Uri committed=DocumentsContract.renameDocument(resolver,temporary,name);if(committed==null)throw new IOException("Cannot finalize file");temporary=null;
   progress.update("Installed "+path);
   return new JSONObject().put("path",path).put("bytes",count).put("sha256",sha).put("verified",true);
  } finally {if(temporary!=null)try{DocumentsContract.deleteDocument(resolver,temporary);}catch(Exception ignored){}}
 }
 public JSONArray inventory()throws Exception {JSONArray items=new JSONArray();walk(root,"",items,0);return items;}
 void walk(Uri dir,String prefix,JSONArray out,int depth)throws Exception {
  if(depth>12)throw new IOException("ROM folder nesting exceeds limit");
  for(Entry e:children(dir)){if(e.name.startsWith("."))continue;String path=prefix+e.name;if(e.dir())walk(e.uri,path+"/",out,depth+1);else out.put(new JSONObject().put("relative",path).put("bytes",e.size));}
 }
}
