package net.thor.roms;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 TextView connection,status,library,folderLabel;EditText search;Spinner folder;String pairing="";JSONArray items=new JSONArray();Handler handler=new Handler();
 final String[] destinations={"switch","switch/updates","switch/dlc","nes","snes","n64","gb","gbc","gba","nds","n3ds","dreamcast","genesis","gamegear","mastersystem","sega32x","segacd","saturn","psx","ps2","ps3","psp","gc","wii","wiiu","xbox","xbox360","atari2600","pcengine","mame","BIOS/switch"};
 Runnable tick=new Runnable(){public void run(){status.setText(ReceiverService.status);handler.postDelayed(this,1000);}};
 TextView text(LinearLayout l,String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setPadding(0,12,0,8);l.addView(v);return v;}
 void button(LinearLayout l,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());l.addView(b);}
 public void onCreate(Bundle state){super.onCreate(state);ScrollView scroll=new ScrollView(this);LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(24,20,24,24);scroll.addView(layout);setContentView(scroll);
  text(layout,"THOR ROM MANAGER",26);text(layout,"Install games over Wi-Fi and manage your SD-card library.",16);
  folderLabel=text(layout,"",14);showFolder();button(layout,"Choose SD card ROMs folder",()->{stopService(new Intent(this,ReceiverService.class));Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);String initial=getSharedPreferences("roms",0).getString("tree",null);if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&getIntent().hasExtra("test_tree"))initial=getIntent().getStringExtra("test_tree");if(initial!=null)i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(initial));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,1);});
  button(layout,"Start Wi-Fi receiver",()->{if(!getSharedPreferences("roms",0).contains("tree")){message("Choose the ROMs folder first");return;}startForegroundService(new Intent(this,ReceiverService.class));loadPairing();});
  button(layout,"Stop receiver",()->{stopService(new Intent(this,ReceiverService.class));ReceiverService.status="Receiver stopped";});
  connection=text(layout,"Pairing details appear after setup.",14);connection.setTextIsSelectable(true);
  button(layout,"Copy desktop pairing details",()->{if(pairing.isEmpty()){loadPairing();return;}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Thor pairing",pairing));message("Copied. Paste into the downloader’s Companion settings.");});
  status=text(layout,ReceiverService.status,16);
  text(layout,"Import files received through LocalSend",20);text(layout,"Select a destination, then choose files in LocalSend’s receive folder. ZIP files are extracted; multipart ZIP, 7z and RAR should be prepared on the desktop. Select updates or DLC explicitly for local Switch imports.",14);
  folder=new Spinner(this);folder.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,destinations));layout.addView(folder);
  button(layout,"Import received files",()->{if(!getSharedPreferences("roms",0).contains("tree")){message("Choose the ROMs folder first");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,2);});
  text(layout,"On this card",20);search=new EditText(this);search.setSingleLine(true);search.setHint("Search title or console folder");layout.addView(search);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderLibrary();}public void afterTextChanged(Editable e){}});
  button(layout,"Refresh library",()->refresh());library=text(layout,"Choose your ROMs folder to browse installed files.",14);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},3);
  handler.post(tick);if(getSharedPreferences("roms",0).contains("tree")){loadPairing();refresh();}
 }
 void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void showFolder(){folderLabel.setText("ROMs folder: "+getSharedPreferences("roms",0).getString("tree","Not selected"));}
 void loadPairing(){new Thread(()->{try{JSONObject info=ReceiverService.pairing(this);pairing=info.toString();runOnUiThread(()->connection.setText("Receiver address: "+info.optString("url")+"\nCopy the pairing details into the desktop downloader. Keep both devices on the same Wi-Fi."));}catch(Exception e){runOnUiThread(()->message(e.toString()));}}).start();}
 void refresh(){library.setText("Reading card…");new Thread(()->{try{JSONArray result=new RomStore(this).inventory();runOnUiThread(()->{items=result;renderLibrary();});}catch(Exception e){runOnUiThread(()->library.setText("Cannot read card: "+e.getMessage()));}}).start();}
 void renderLibrary(){String q=search.getText().toString().toLowerCase(Locale.ROOT);StringBuilder b=new StringBuilder();int count=0;for(int i=0;i<items.length();i++){JSONObject row=items.optJSONObject(i);String path=row.optString("relative");if(path.toLowerCase(Locale.ROOT).contains(q)){count++;b.append(path).append("\n").append(String.format(Locale.ROOT,"%.1f MiB",row.optLong("bytes")/1048576.0)).append("\n\n");}}library.setText(count+" files\n\n"+b);}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
  try{if(request==1){Uri uri=data.getData();int grants=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getContentResolver().takePersistableUriPermission(uri,grants);getSharedPreferences("roms",0).edit().putString("tree",uri.toString()).commit();showFolder();refresh();loadPairing();}
   else if(request==2){ArrayList<String> values=new ArrayList<>();ClipData clip=data.getClipData();if(clip!=null)for(int n=0;n<clip.getItemCount();n++)values.add(clip.getItemAt(n).getUri().toString());else values.add(data.getData().toString());for(String v:values)getContentResolver().takePersistableUriPermission(Uri.parse(v),Intent.FLAG_GRANT_READ_URI_PERMISSION);Intent i=new Intent(this,ReceiverService.class).putExtra("imports",values.toArray(new String[0])).putExtra("folder",folder.getSelectedItem().toString());startForegroundService(i);}
  }catch(Exception e){message(e.getMessage());}
 }
 protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
}
