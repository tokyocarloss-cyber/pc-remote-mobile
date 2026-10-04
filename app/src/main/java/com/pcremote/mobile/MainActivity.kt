package com.pcremote.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF070A12)
private val Card = Color(0xFF121827)
private val Cyan = Color(0xFF27E5FF)
private val Purple = Color(0xFF9D5CFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { PCRemoteApp() } }
    }
}

@Composable
fun PCRemoteApp() {
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Controle", "Apps", "Touchpad", "Enviar", "Tela")
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("PC REMOTE", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Central de controle", color = Color.Gray) }
            Surface(shape = RoundedCornerShape(30.dp), color = Card) { Text("● PC aguardando", Modifier.padding(12.dp,8.dp), color = Cyan) }
        }
        Spacer(Modifier.height(22.dp))
        when(tab) {
            0 -> ControllerScreen()
            1 -> AppsScreen()
            2 -> TouchpadScreen()
            3 -> TransferScreen()
            else -> ScreenView()
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            names.forEachIndexed { i, name -> Text(name, color = if(tab==i) Cyan else Color.LightGray, fontSize = 12.sp, modifier = Modifier.clickable { tab=i }.padding(9.dp)) }
        }
    }
}

@Composable fun ControllerScreen() {
    Column { Text("Controle", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold); Text("Layout móvel • personalizável", color=Color.Gray); Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Box(Modifier.size(135.dp).background(Card, RoundedCornerShape(70.dp)), contentAlignment=Alignment.Center){ Text("◎", color=Cyan, fontSize=54.sp) }
            Column { Row { GameButton("Y"); GameButton("B") }; Row { GameButton("X"); GameButton("A") } }
        }
        Spacer(Modifier.height(20.dp)); Row { ActionCard("Perfil automático", "Detecta o jogo aberto", Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); ActionCard("Editar layout", "Mover, redimensionar e criar botões", Modifier.weight(1f)) }
    }
}
@Composable fun GameButton(t:String){ Box(Modifier.padding(4.dp).size(58.dp).background(Card, RoundedCornerShape(30.dp)), contentAlignment=Alignment.Center){Text(t,color=Purple,fontWeight=FontWeight.Bold)} }
@Composable fun AppsScreen(){ Column { Text("Apps & Jogos",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold); Text("Segure no centro e arraste para abrir",color=Color.Gray); Spacer(Modifier.height(35.dp)); Box(Modifier.fillMaxWidth().height(280.dp).background(Card,RoundedCornerShape(140.dp)),contentAlignment=Alignment.Center){ Column(horizontalAlignment=Alignment.CenterHorizontally){Text("◉",color=Cyan,fontSize=64.sp);Text("SEGURE",color=Color.White,fontWeight=FontWeight.Bold);Text("Roda rápida de aplicativos",color=Color.Gray)}} } }
@Composable fun TouchpadScreen(){ Column { Text("Touchpad",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(14.dp)); Box(Modifier.fillMaxWidth().height(330.dp).background(Card,RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Text("Deslize para controlar o mouse\nToque = clique • 2 dedos = rolagem",color=Color.LightGray)} } }
@Composable fun TransferScreen(){ Column { Text("Transferência instantânea",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold); Text("Celular ⇄ PC",color=Color.Gray); Spacer(Modifier.height(18.dp)); Box(Modifier.fillMaxWidth().height(230.dp).background(Card,RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("⇅",color=Cyan,fontSize=60.sp);Text("Solte ou compartilhe arquivos aqui",color=Color.White);Text("Fotos • vídeos • links • documentos • texto",color=Color.Gray)}} } }
@Composable fun ScreenView(){ Column { Text("Tela do PC",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(18.dp)); Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black,RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){Text("Streaming da tela\nAguardando PC…",color=Color.Gray)} } }
@Composable fun ActionCard(title:String,sub:String,modifier:Modifier=Modifier){ Column(modifier.background(Card,RoundedCornerShape(18.dp)).padding(14.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(sub,color=Color.Gray,fontSize=12.sp)} }
