package net.thor.roms;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 static final int BG=0xff101318,CARD=0xff1a2028,BORDER=0xff303a46,INK=0xfff0f4f8,MUTED=0xffa7b3c2,ACCENT=0xffb9f276;
 TextView connection,status,library,folderLabel,badge,headline,subtitle,fileName,phase,sizeValue,speedValue,steps,awakeHint;
 EditText search;Spinner folder;Button receiver;Ring ring;View[] pages=new View[3];Button[] tabs=new Button[3];FrameLayout content;int currentTab=0;
 String pairing="";JSONArray items=new JSONArray();ArrayList<JSONObject> visible=new ArrayList<>();BaseAdapter libraryAdapter;Handler handler=new Handler();
 final String[] destinations={"switch","switch/updates","switch/dlc","nes","snes","n64","gb","gbc","gba","nds","n3ds","dreamcast","genesis","gamegear","mastersystem","sega32x","segacd","saturn","psx","ps2","ps3","psp","gc","wii","wiiu","xbox","xbox360","atari2600","pcengine","mame","BIOS/switch"};
 Runnable tick=new Runnable(){public void run(){updateDashboard();updateKeepAwake();handler.postDelayed(this,1000);}};
 int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),BORDER);return d;}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 TextView text(LinearLayout parent,String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");v.setLineSpacing(dp(3),1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);parent.addView(v,p);return v;}
 TextView heading(LinearLayout p,String value,int size){TextView v=text(p,value,size,INK);v.setTypeface(Typeface.create("sans-serif-medium",0));return v;}
 LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(20),dp(20),dp(20),dp(16));c.setBackground(surface(CARD,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);parent.addView(c,p);return c;}
 Button button(LinearLayout p,String label,boolean primary,Runnable click){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(primary?BG:INK);b.setMinHeight(dp(52));b.setPadding(dp(16),dp(8),dp(16),dp(8));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),surface(primary?ACCENT:0xff252e39,14),null));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);p.addView(b,lp);b.setOnClickListener(v->click.run());return b;}
 LinearLayout scrollPage(int index){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);LinearLayout l=column();l.setPadding(dp(20),dp(20),dp(20),dp(12));sc.addView(l);pages[index]=sc;content.addView(sc);return l;}
 public void onCreate(Bundle saved){super.onCreate(saved);LinearLayout root=column();root.setBackgroundColor(BG);setContentView(root);
  LinearLayout mast=column();mast.setPadding(dp(24),dp(16),dp(24),dp(4));root.addView(mast);text(mast,"T H O R   /   ROM MANAGER",12,ACCENT);
  content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));buildTransfer(scrollPage(0));buildLibrary();buildOptions(scrollPage(2));
  LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(12),dp(8),dp(12),dp(8));nav.setBackground(surface(CARD,0));root.addView(nav);String[] names={"Transfer","Library","Options"};
  for(int i=0;i<3;i++){final int index=i;Button b=new Button(this);tabs[i]=b;b.setText(names[i]);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(52));b.setOnClickListener(v->selectTab(index));nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}
  selectTab(saved==null?0:saved.getInt("tab",0));showFolder();handler.post(tick);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},3);
  if(getSharedPreferences("roms",0).contains("tree")){loadPairing();refresh();}
 }
 void selectTab(int index){currentTab=index;for(int i=0;i<3;i++){pages[i].setVisibility(i==index?View.VISIBLE:View.GONE);tabs[i].setTextColor(i==index?ACCENT:MUTED);tabs[i].setBackground(surface(i==index?0xff2b3727:CARD,14));tabs[i].setSelected(i==index);}pages[index].setAlpha(.6f);pages[index].animate().alpha(1).setDuration(140).start();}
 void buildTransfer(LinearLayout page){
  boolean wide=getResources().getConfiguration().screenWidthDp>=600;
  LinearLayout panels=new LinearLayout(this);panels.setOrientation(wide?0:1);page.addView(panels);
  LinearLayout c=card(panels);if(wide){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,0.85f);lp.rightMargin=dp(12);c.setLayoutParams(lp);}badge=text(c,"●  RECEIVER OFF",12,ACCENT);headline=heading(c,"Ready when you are.",28);subtitle=text(c,"",15,MUTED);
  receiver=button(c,"Start receiver",true,()->{if(!getSharedPreferences("roms",0).contains("tree")){selectTab(2);chooseFolder();}else if(ReceiverService.running){stopService(new Intent(this,ReceiverService.class));ReceiverService.status="Receiver stopped";}else{startForegroundService(new Intent(this,ReceiverService.class));loadPairing();}});
  c=card(panels);if(wide)c.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.15f));text(c,"TRANSFER",12,MUTED);
  LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setOrientation(wide?0:1);c.addView(hero);
  ring=new Ring();hero.addView(ring,new LinearLayout.LayoutParams(dp(wide?94:136),dp(wide?94:136)));
  LinearLayout detail=column();detail.setPadding(wide?dp(12):0,dp(8),0,0);hero.addView(detail,new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1:0));phase=heading(detail,"Your next game awaits",20);fileName=text(detail,"Start a transfer from your computer.",15,MUTED);
  steps=text(c,"01  Receive     /     02  Verify     /     03  Ready",12,MUTED);
  LinearLayout metrics=new LinearLayout(this);c.addView(metrics);LinearLayout left=column(),right=column();metrics.addView(left,new LinearLayout.LayoutParams(0,-2,1));metrics.addView(right,new LinearLayout.LayoutParams(0,-2,1));text(left,"RECEIVED",11,MUTED);sizeValue=heading(left,"—",17);text(right,"AVG. SPEED",11,MUTED);speedValue=heading(right,"—",17);
  status=text(c,"",13,MUTED);awakeHint=text(page,"",12,MUTED);
 }
 void buildLibrary(){LinearLayout page=column();page.setPadding(dp(20),dp(20),dp(20),0);pages[1]=page;content.addView(page);heading(page,"Your library.",28);library=text(page,"Choose a ROMs folder in Options to get started.",14,MUTED);
  search=new EditText(this);search.setSingleLine(true);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setTextSize(16);search.setHint("Search your games");search.setPadding(dp(16),dp(12),dp(16),dp(12));search.setBackground(surface(CARD,16));page.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){renderLibrary();}public void afterTextChanged(Editable e){}});
  button(page,"Refresh library",false,()->refresh());ListView list=new ListView(this);list.setDividerHeight(dp(8));list.setDivider(new android.graphics.drawable.ColorDrawable(BG));list.setPadding(0,dp(16),0,0);list.setClipToPadding(false);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  libraryAdapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}public View getView(int p,View reuse,android.view.ViewGroup parent){LinearLayout row;TextView title,meta;if(reuse==null){row=column();row.setPadding(dp(16),dp(14),dp(16),dp(10));row.setBackground(surface(CARD,16));title=heading(row,"",16);meta=text(row,"",12,MUTED);}else{row=(LinearLayout)reuse;title=(TextView)row.getChildAt(0);meta=(TextView)row.getChildAt(1);}JSONObject item=visible.get(p);String path=item.optString("relative");int slash=path.lastIndexOf('/');title.setText(path.substring(slash+1));meta.setText((slash>=0?path.substring(0,slash):"ROM")+"  ·  "+bytes(item.optLong("bytes")));return row;}};list.setAdapter(libraryAdapter);
 }
 void buildOptions(LinearLayout page){heading(page,"Make it yours.",28);text(page,"Connection, storage and transfer preferences.",15,MUTED);
  LinearLayout c=card(page);heading(c,"SD card",20);folderLabel=text(c,"",14,MUTED);button(c,"Choose ROMs folder",false,()->chooseFolder());
  c=card(page);heading(c,"Pair your computer",20);text(c,"Use the same Wi-Fi on both devices. Copy pairing details into Transfer destination on the computer, then select Pair and use Wi-Fi.",14,MUTED);connection=text(c,"Start the receiver to see your address.",14,INK);connection.setTextIsSelectable(true);button(c,"Copy pairing details",true,()->{if(pairing.isEmpty()){loadPairing();message("Preparing details. Tap again in a moment.");return;}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Thor pairing",pairing));message("Copied. Paste into Transfer destination on your computer.");});
  c=card(page);heading(c,"Keep transfers awake",20);Switch keep=new Switch(this);keep.setText("Keep screen awake");keep.setTextColor(INK);keep.setTextSize(16);keep.setMinHeight(dp(52));keep.setChecked(getSharedPreferences("roms",0).getBoolean("keep_screen_awake",true));c.addView(keep);keep.setOnCheckedChangeListener((b,on)->{getSharedPreferences("roms",0).edit().putBoolean("keep_screen_awake",on).apply();updateKeepAwake();});text(c,"While the receiver is on, leave this app visible to prevent screen timeout. Stop the receiver to allow normal sleep. The power button and lid can still put the device to sleep.",14,MUTED);
  c=card(page);heading(c,"Import from LocalSend",20);text(c,"Choose a destination and received files. ZIPs are extracted. Prepare multipart ZIP, 7z and RAR on the computer first.",14,MUTED);folder=new Spinner(this);folder.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,destinations));c.addView(folder,new LinearLayout.LayoutParams(-1,dp(52)));button(c,"Choose received files",false,()->{if(!getSharedPreferences("roms",0).contains("tree")){chooseFolder();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,2);});
 }
 void chooseFolder(){stopService(new Intent(this,ReceiverService.class));Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);String initial=getSharedPreferences("roms",0).getString("tree",null);if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&getIntent().hasExtra("test_tree"))initial=getIntent().getStringExtra("test_tree");if(initial!=null)i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(initial));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,1);}
 void setLabel(TextView v,String s){if(!s.contentEquals(v.getText()))v.setText(s);}
 String bytes(long n){return n>=1073741824L?String.format(Locale.ROOT,"%.2f GiB",n/1073741824.0):String.format(Locale.ROOT,"%.1f MiB",n/1048576.0);}
 void updateDashboard(){boolean configured=getSharedPreferences("roms",0).contains("tree"),running=ReceiverService.running;long age=System.currentTimeMillis()-ReceiverService.lastContact;boolean connected=running&&(ReceiverService.activeRequests.get()>0||age<45000);
  setLabel(badge,connected?"●  COMPUTER CONNECTED":running?"●  WAITING FOR COMPUTER":"○  RECEIVER OFF");badge.setTextColor(connected?ACCENT:MUTED);
  setLabel(headline,!configured?"Welcome to your Thor.":connected?"You're connected.":running?"Let's connect.":"Ready when you are.");
  setLabel(subtitle,!configured?"Choose your SD card ROMs folder to start building your library.":connected?ReceiverService.desktop+" · Authenticated over Wi-Fi":running?"Same Wi-Fi. Open Options to copy pairing details, then paste them into Transfer destination on your computer.":"Start the receiver to send games from your computer.");
  setLabel(receiver,!configured?"Choose ROMs folder":running?"Stop receiver":"Start receiver");
  boolean has=!ReceiverService.transferFile.isEmpty(),active=ReceiverService.transferring;long done=ReceiverService.bytesReceived,total=ReceiverService.transferSize;String stage=ReceiverService.transferPhase;boolean verifying=active&&stage.startsWith("Verifying"),complete=stage.startsWith("Complete")||stage.startsWith("Already installed");boolean failed=stage.startsWith("Interrupted")||stage.startsWith("Transfer failed");
  setLabel(phase,!has?"Your next game awaits":verifying?"Making sure it’s safe.":complete?"Ready to play.":failed?"Transfer needs attention":active?"On its way.":stage);
  setLabel(fileName,has?ReceiverService.transferFile:"Your file and live progress will appear here.");
  setLabel(sizeValue,has?bytes(done)+" / "+bytes(total):"—");double seconds=Math.max(1,(System.currentTimeMillis()-ReceiverService.transferStarted)/1000.0);setLabel(speedValue,active&&!verifying?String.format(Locale.ROOT,"%.1f MiB/s",done/1048576.0/seconds):"—");
  setLabel(steps,complete?"✓  Received     /     ✓  Verified     /     ✓  Ready":verifying?"✓  Received     /     ●  Verifying     /     03  Ready":"01  Receive     /     02  Verify     /     03  Ready");steps.setTextColor(complete?ACCENT:MUTED);
  setLabel(status,has?stage:ReceiverService.status);status.setTextColor(failed?0xffffb4a5:MUTED);
  int percent=total>0?(int)Math.min(100,done*100/total):0;ring.update(has?percent:0,!has?"READY":verifying?"VERIFY":failed?"RETRY":complete?"DONE":percent+"%",failed?0xffffb4a5:ACCENT);
  setLabel(awakeHint,running&&getSharedPreferences("roms",0).getBoolean("keep_screen_awake",true)?"☀  Screen stays awake while this app is visible":"Screen timeout follows your device settings.");
 }
 class Ring extends View {Paint p=new Paint(3);int value=0,color=ACCENT;String label="READY";Ring(){super(MainActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);}void update(int v,String s,int c){if(v==value&&s.equals(label)&&c==color)return;value=v;label=s;color=c;setContentDescription("Transfer progress "+s);invalidate();}protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(13);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(8));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(BORDER);c.drawCircle(x,y,r,p);p.setColor(color);c.drawArc(new RectF(x-r,y-r,x+r,y+r),-90,value*3.6f,false,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(dp(label.endsWith("%")?26:16));c.drawText(label,x,y-(p.ascent()+p.descent())/2,p);}}
 void updateKeepAwake(){boolean enabled=getSharedPreferences("roms",0).getBoolean("keep_screen_awake",true)&&ReceiverService.running;if(enabled)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
 protected void onResume(){super.onResume();updateKeepAwake();}
 void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void showFolder(){String value=getSharedPreferences("roms",0).getString("tree",null);String name="Not selected";if(value!=null)try{name=android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(value)).replace(":"," / ");}catch(Exception e){name="Selected SD card folder";}folderLabel.setText(name);}
 void loadPairing(){new Thread(()->{try{JSONObject info=ReceiverService.pairing(this);pairing=info.toString();runOnUiThread(()->connection.setText("Receiver address: "+info.optString("url")+"\nCopy the pairing details into the desktop downloader. Keep both devices on the same Wi-Fi."));}catch(Exception e){runOnUiThread(()->message(e.toString()));}}).start();}
 void refresh(){library.setText("Reading card…");new Thread(()->{try{JSONArray result=new RomStore(this).inventory();runOnUiThread(()->{items=result;renderLibrary();});}catch(Exception e){runOnUiThread(()->library.setText("Cannot read card: "+e.getMessage()));}}).start();}
 void renderLibrary(){if(libraryAdapter==null)return;String q=search.getText().toString().toLowerCase(Locale.ROOT);visible.clear();long size=0;for(int i=0;i<items.length();i++){JSONObject row=items.optJSONObject(i);if(row.optString("relative").toLowerCase(Locale.ROOT).contains(q)){visible.add(row);size+=row.optLong("bytes");}}library.setText(visible.size()+" files · "+bytes(size));libraryAdapter.notifyDataSetChanged();}
 protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",currentTab);}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
  try{if(request==1){Uri uri=data.getData();int grants=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getContentResolver().takePersistableUriPermission(uri,grants);getSharedPreferences("roms",0).edit().putString("tree",uri.toString()).commit();showFolder();refresh();loadPairing();}
   else if(request==2){ArrayList<String> values=new ArrayList<>();ClipData clip=data.getClipData();if(clip!=null)for(int n=0;n<clip.getItemCount();n++)values.add(clip.getItemAt(n).getUri().toString());else values.add(data.getData().toString());for(String v:values)getContentResolver().takePersistableUriPermission(Uri.parse(v),Intent.FLAG_GRANT_READ_URI_PERMISSION);Intent i=new Intent(this,ReceiverService.class).putExtra("imports",values.toArray(new String[0])).putExtra("folder",folder.getSelectedItem().toString());startForegroundService(i);}
  }catch(Exception e){message(e.getMessage());}
 }
 protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
}
