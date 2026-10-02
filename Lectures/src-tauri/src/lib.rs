use serde::{Deserialize, Serialize};
use std::{
    env,
    io::{Read, Write},
    net::{TcpListener, TcpStream, UdpSocket},
    sync::{atomic::{AtomicBool, Ordering}, Arc},
    thread,
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tauri::{AppHandle, Emitter, State, Theme, Window};
use local_ip_address::list_afinet_netifas;

const TRANSFER_PORT: u16 = 47831;
const DISCOVERY_PORT: u16 = 47832;
const DISCOVER: &str = "LECTURES_DISCOVER_V2";
const PROTOCOL: &str = "LECTURES_SYNC_V2";

struct SyncState { started: Arc<AtomicBool>, accepting: Arc<AtomicBool>, device_id: String }
impl Default for SyncState {
    fn default() -> Self {
        let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos();
        let device_id = format!("{}-{}-{}", device_name(), std::process::id(), now);
        Self { started: Arc::new(AtomicBool::new(false)), accepting: Arc::new(AtomicBool::new(false)), device_id }
    }
}

#[derive(Serialize, Deserialize, Clone)]
struct Peer { id: String, name: String, ip: String, port: u16 }

fn device_name() -> String {
    env::var("COMPUTERNAME")
        .or_else(|_| env::var("HOSTNAME"))
        .ok()
        .filter(|s| !s.trim().is_empty())
        .unwrap_or_else(|| if cfg!(target_os="android") { "Android-устройство".into() } else { "Компьютер".into() })
}

fn read_request(mut stream: &TcpStream) -> Result<String, String> {
    stream.set_read_timeout(Some(Duration::from_secs(30))).map_err(|e| e.to_string())?;
    let mut header = Vec::new(); let mut b=[0u8;1];
    while header.len() < 16384 {
        let n=stream.read(&mut b).map_err(|e|e.to_string())?; if n==0 {break} header.push(b[0]);
        if header.ends_with(b"\r\n\r\n") {break}
    }
    let hs=String::from_utf8_lossy(&header);
    let len=hs.lines().find_map(|l| l.strip_prefix("Content-Length: ").or_else(||l.strip_prefix("content-length: "))).and_then(|v|v.trim().parse::<usize>().ok()).unwrap_or(0);
    if len > 200*1024*1024 { return Err("Пакет слишком большой".into()); }
    let mut body=vec![0u8;len]; stream.read_exact(&mut body).map_err(|e|e.to_string())?;
    String::from_utf8(body).map_err(|_|"Некорректный UTF-8".into())
}

fn reply(mut s: TcpStream, status:&str, body:&str) {
    let r=format!("HTTP/1.1 {}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",status,body.as_bytes().len(),body);
    let _=s.write_all(r.as_bytes());
}

fn start_tcp_server(app: AppHandle, accepting: Arc<AtomicBool>) -> Result<(), String> {
    let listener=TcpListener::bind(("0.0.0.0",TRANSFER_PORT)).map_err(|e|format!("Порт {} недоступен: {}",TRANSFER_PORT,e))?;
    thread::spawn(move || for incoming in listener.incoming() {
        if let Ok(stream)=incoming {
            let app2=app.clone();
            let accepting2=accepting.clone();
            thread::spawn(move || {
                let result=read_request(&stream).and_then(|body| {
                    let v:serde_json::Value=serde_json::from_str(&body).map_err(|e|e.to_string())?;
                    if v.get("protocol").and_then(|x|x.as_str()) != Some(PROTOCOL) { return Err("Неизвестный протокол".into()); }
                    if !accepting2.load(Ordering::SeqCst) { return Err("На этом компьютере не включён приём данных".into()); }
                    let payload=v.get("payload").and_then(|x|x.as_str()).ok_or("Нет данных")?;
                    app2.emit("sync-data-received",payload).map_err(|e|e.to_string())?; Ok(())
                });
                match result { Ok(())=>reply(stream,"200 OK","OK"), Err(e)=>reply(stream,"400 Bad Request",&e) }
            });
        }
    });
    Ok(())
}

fn start_discovery_responder(device_id: String) -> Result<(), String> {
    let socket=UdpSocket::bind(("0.0.0.0",DISCOVERY_PORT)).map_err(|e|format!("Порт обнаружения {} недоступен: {}",DISCOVERY_PORT,e))?;
    thread::spawn(move || {
        let mut buf=[0u8;512];
        loop {
            if let Ok((n,src))=socket.recv_from(&mut buf) {
                let message = String::from_utf8_lossy(&buf[..n]);
                let requester_id = serde_json::from_str::<serde_json::Value>(&message)
                    .ok().and_then(|v| v.get("id").and_then(|x| x.as_str()).map(str::to_owned));
                let is_discovery = message.trim()==DISCOVER || serde_json::from_str::<serde_json::Value>(&message)
                    .ok().and_then(|v| v.get("type").and_then(|x| x.as_str()).map(|x| x==DISCOVER)).unwrap_or(false);
                if is_discovery && requester_id.as_deref()!=Some(device_id.as_str()) {
                    let response=serde_json::json!({"id":device_id,"name":device_name(),"port":TRANSFER_PORT}).to_string();
                    let _=socket.send_to(response.as_bytes(),src);
                }
            }
        }
    });
    Ok(())
}

#[tauri::command]
fn start_peer_service(app: AppHandle, state: State<SyncState>) -> Result<(),String> {
    if state.started.swap(true, Ordering::SeqCst) { return Ok(()); }
    if let Err(e)=start_tcp_server(app, state.accepting.clone()) { state.started.store(false, Ordering::SeqCst); return Err(e); }
    if let Err(e)=start_discovery_responder(state.device_id.clone()) { state.started.store(false, Ordering::SeqCst); return Err(e); }
    Ok(())
}

#[tauri::command]
fn set_accepting(state: State<SyncState>, value: bool) -> bool {
    state.accepting.store(value, Ordering::SeqCst);
    value
}

#[tauri::command]
fn get_accepting(state: State<SyncState>) -> bool {
    state.accepting.load(Ordering::SeqCst)
}

#[tauri::command]
fn discover_peers(state: State<SyncState>) -> Result<Vec<Peer>,String> {
    let socket=UdpSocket::bind(("0.0.0.0",0)).map_err(|e|e.to_string())?;
    socket.set_broadcast(true).map_err(|e|e.to_string())?;
    socket.set_read_timeout(Some(Duration::from_millis(180))).map_err(|e|e.to_string())?;
    let discovery=serde_json::json!({"type":DISCOVER,"id":state.device_id}).to_string();

    // 255.255.255.255 can be routed through the wrong adapter on Windows
    // when VPN/Hyper-V/virtual adapters are installed. Send to both the
    // limited broadcast and /24 broadcasts derived from every active IPv4
    // interface so phones on the same Wi-Fi are reliably reached.
    let mut targets = vec![format!("255.255.255.255:{}", DISCOVERY_PORT)];
    if let Ok(ifaces) = list_afinet_netifas() {
        for (_name, ip) in ifaces {
            if let std::net::IpAddr::V4(v4) = ip {
                if v4.is_loopback() || v4.is_link_local() { continue; }
                let o = v4.octets();
                let target = format!("{}.{}.{}.255:{}", o[0], o[1], o[2], DISCOVERY_PORT);
                if !targets.contains(&target) { targets.push(target); }
            }
        }
    }

    for target in &targets { let _ = socket.send_to(discovery.as_bytes(), target); }
    let deadline=Instant::now()+Duration::from_millis(1650);
    let resend_at=Instant::now()+Duration::from_millis(420);
    let mut resent=false; let mut peers=Vec::new(); let mut buf=[0u8;1024];
    while Instant::now()<deadline {
        if !resent && Instant::now()>=resend_at {
            for target in &targets { let _ = socket.send_to(discovery.as_bytes(), target); }
            resent=true;
        }
        match socket.recv_from(&mut buf) {
            Ok((n,src)) => if let Ok(v)=serde_json::from_slice::<serde_json::Value>(&buf[..n]) {
                let id=v.get("id").and_then(|x|x.as_str()).unwrap_or("").to_string();
                if id.is_empty() || id==state.device_id { continue; }
                let name=v.get("name").and_then(|x|x.as_str()).unwrap_or("Устройство").to_string();
                let port=v.get("port").and_then(|x|x.as_u64()).unwrap_or(TRANSFER_PORT as u64) as u16;
                let ip=src.ip().to_string();
                if !peers.iter().any(|p:&Peer|p.id==id) { peers.push(Peer{id,name,ip,port}); }
            },
            Err(e) if e.kind()==std::io::ErrorKind::WouldBlock || e.kind()==std::io::ErrorKind::TimedOut => {},
            Err(_) => break,
        }
    }
    Ok(peers)
}

#[tauri::command]
fn send_sync_data(state: State<SyncState>, peer_id:String, ip:String, port:u16, payload:String)->Result<String,String>{
    if peer_id==state.device_id { return Err("Нельзя передать данные на это же устройство".into()); }
    if payload.len()>200*1024*1024{return Err("Пакет больше 200 МБ".into())}
    let body=serde_json::json!({"protocol":PROTOCOL,"payload":payload}).to_string();
    let addr=format!("{}:{}",ip,port).parse().map_err(|_|"Некорректный адрес устройства")?;
    let mut s=TcpStream::connect_timeout(&addr,Duration::from_secs(5)).map_err(|e|format!("Не удалось подключиться: {}",e))?;
    s.set_read_timeout(Some(Duration::from_secs(30))).ok();
    let req=format!("POST /sync HTTP/1.1\r\nHost: {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",ip,body.as_bytes().len(),body);
    s.write_all(req.as_bytes()).map_err(|e|e.to_string())?; let mut resp=String::new(); s.read_to_string(&mut resp).map_err(|e|e.to_string())?;
    if !resp.starts_with("HTTP/1.1 200") {return Err(resp.split("\r\n\r\n").nth(1).unwrap_or("Ошибка принимающего устройства").into())}
    Ok("Данные переданы".into())
}

#[tauri::command]
fn set_window_theme(window: Window, theme: String) -> Result<(), String> {
    let theme = if theme=="light" { Theme::Light } else { Theme::Dark };
    window.set_theme(Some(theme)).map_err(|e| e.to_string())
}

#[tauri::command]
fn save_export_json(contents: String, default_name: String) -> Result<Option<String>, String> {
    let path = rfd::FileDialog::new()
        .set_title("Сохранить резервную копию")
        .set_file_name(&default_name)
        .add_filter("JSON", &["json"])
        .save_file();

    let Some(path) = path else {
        return Ok(None);
    };

    std::fs::write(&path, contents.as_bytes())
        .map_err(|e| format!("Не удалось сохранить файл: {}", e))?;

    Ok(Some(path.to_string_lossy().into_owned()))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run(){tauri::Builder::default().manage(SyncState::default()).invoke_handler(tauri::generate_handler![start_peer_service,set_accepting,get_accepting,discover_peers,send_sync_data,set_window_theme,save_export_json]).run(tauri::generate_context!()).expect("error while running app");}
