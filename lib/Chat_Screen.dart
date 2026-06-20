import 'dart:async';
import 'package:flutter/material.dart';
import 'Native_Bridge_Service.dart';

class Chat_Screen extends StatefulWidget {
  const Chat_Screen({Key? Key_Val}) : super(key: Key_Val);

  @override
  State<Chat_Screen> createState() => _Chat_Screen_State();
}

class _Chat_Screen_State extends State<Chat_Screen> with WidgetsBindingObserver {
  final TextEditingController _Input_Controller = TextEditingController();
  final ScrollController _Scroll_Controller = ScrollController();
  
  final List<Map<String, String>> _Messages = [];
  bool _Is_Generating = false;
  StreamSubscription<String>? _Inference_Subscription;

  bool _Accessibility_Active = false;
  bool _Notification_Active = false;
  bool _Overlay_Active = false;
  bool _Is_Syncing = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _Messages.add({
      'sender': 'agent',
      'text': 'Hola, soy tu Agente Local con Memoria RAG. He indexado tus conversaciones de WhatsApp y transacciones financieras locales de forma segura. ¿Qué deseas consultar hoy?',
    });
    _Update_Permissions_Status();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _Inference_Subscription?.cancel();
    _Input_Controller.dispose();
    _Scroll_Controller.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState State) {
    if (State == AppLifecycleState.resumed) {
      _Update_Permissions_Status();
      _Sync_Logs_On_Resume();
    }
  }

  Future<void> _Update_Permissions_Status() async {
    final bool Acc = await Native_Bridge_Service.Is_Service_Enabled('accessibility');
    final bool Not = await Native_Bridge_Service.Is_Service_Enabled('notification');
    final bool Ovr = await Native_Bridge_Service.Is_Service_Enabled('overlay');
    
    setState(() {
      _Accessibility_Active = Acc;
      _Notification_Active = Not;
      _Overlay_Active = Ovr;
    });
  }

  Future<void> _Sync_Logs_On_Resume() async {
    setState(() {
      _Is_Syncing = true;
    });
    final int New_Logs = await Native_Bridge_Service.Force_Batch_Process();
    setState(() {
      _Is_Syncing = false;
    });
    if (New_Logs > 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text("Sincronización reactiva completada: $New_Logs nuevos logs procesados."),
          backgroundColor: Colors.cyan,
        ),
      );
    }
  }

  void _Send_Message() async {
    final String Prompt = _Input_Controller.text.trim();
    if (Prompt.isEmpty || _Is_Generating) return;

    setState(() {
      _Messages.add({'sender': 'user', 'text': Prompt});
      _Messages.add({'sender': 'agent', 'text': ''});
      _Is_Generating = true;
      _Input_Controller.clear();
    });

    _Scroll_To_Bottom();

    final List<dynamic> Context_Matches = await Native_Bridge_Service.Search_Vector_Logs(Prompt, Limit: 3);

    String Rag_Context = "";
    if (Context_Matches.isNotEmpty) {
      Rag_Context = "\n[CONTEXTO RECUPERADO DE LA MEMORIA LOCAL EN TIEMPO REAL]\n" +
          Context_Matches.map((Match) {
            final double Sim = Match['similarity'] ?? 0.0;
            return "- [App: ${Match['title']} (Sim: ${Sim.toStringAsFixed(2)})]: ${Match['rawText']}";
          }).join("\n");
    }

    final String System_Instruction =
        "SYSTEM: Eres un Agente IA Personal Local de Android. Responde las dudas del usuario usando únicamente los datos de contexto recuperados a continuación si son relevantes. Mantén las respuestas breves y directas.\n$Rag_Context\n\nUSER: $Prompt\nASSISTANT:";

    _Inference_Subscription?.cancel();
    _Inference_Subscription = Native_Bridge_Service.Listen_To_Inference().listen((Token) {
      setState(() {
        if (_Messages.isNotEmpty) {
          final int Last_Idx = _Messages.length - 1;
          _Messages[Last_Idx]['text'] = (_Messages[Last_Idx]['text'] ?? "") + Token;
        }
      });
      _Scroll_To_Bottom();
    }, onDone: () {
      setState(() {
        _Is_Generating = false;
      });
      _Process_Tool_Calling_If_Required();
    }, onError: (Error) {
      setState(() {
        _Is_Generating = false;
      });
    });

    await Native_Bridge_Service.Run_Inference(System_Instruction);
  }

  void _Process_Tool_Calling_If_Required() {
    if (_Messages.isNotEmpty) {
      final String Last_Text = _Messages.last['text'] ?? "";
      if (Last_Text.contains('"action": "show_bubble"')) {
        Native_Bridge_Service.Show_Overlay_Bubble();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("Burbuja flotante activada por herramienta del LLM"),
            backgroundColor: Colors.purple,
          ),
        );
      }
    }
  }

  void _Scroll_To_Bottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_Scroll_Controller.hasClients) {
        _Scroll_Controller.animateTo(
          _Scroll_Controller.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0B10),
      appBar: AppBar(
        backgroundColor: const Color(0xFF13111C),
        elevation: 0,
        title: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: _Is_Syncing ? Colors.amber : Colors.greenAccent,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: _Is_Syncing ? Colors.amber : Colors.greenAccent.withOpacity(0.5),
                    blurRadius: 6,
                    spreadRadius: 2,
                  )
                ],
              ),
            ),
            const SizedBox(width: 10),
            const Text(
              "Agente IA Personal",
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 16,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: Icon(
              _Is_Syncing ? Icons.sync : Icons.sync_outlined,
              color: Colors.cyanAccent,
            ),
            onPressed: _Is_Syncing ? null : _Sync_Logs_On_Resume,
          )
        ],
      ),
      body: Column(
        children: [
          Container(
            margin: const EdgeInsets.all(12),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0x13FFFFFF),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0x1AFFFFFF), width: 1.5),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Configuración del Contexto Local (Kotlin)",
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    _Build_Service_Badge("Chats WhatsApp", _Accessibility_Active, () {
                      Native_Bridge_Service.Request_Permissions("accessibility");
                    }),
                    _Build_Service_Badge("Transacciones", _Notification_Active, () {
                      Native_Bridge_Service.Request_Permissions("notification");
                    }),
                    _Build_Service_Badge("Burbuja Chat", _Overlay_Active, () async {
                      if (_Overlay_Active) {
                        await Native_Bridge_Service.Hide_Overlay_Bubble();
                      } else {
                        await Native_Bridge_Service.Request_Permissions("overlay");
                        await Native_Bridge_Service.Show_Overlay_Bubble();
                      }
                      _Update_Permissions_Status();
                    }),
                  ],
                ),
              ],
            ),
          ),
          Expanded(
            child: ListView.builder(
              controller: _Scroll_Controller,
              padding: const EdgeInsets.all(16),
              itemCount: _Messages.length,
              itemBuilder: (Ctx, Idx) {
                final Msg = _Messages[Idx];
                final Is_User = Msg['sender'] == 'user';
                return Align(
                  alignment: Is_User ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    margin: const EdgeInsets.symmetric(vertical: 8),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    constraints: BoxConstraints(
                      maxWidth: MediaQuery.of(context).size.width * 0.8,
                    ),
                    decoration: BoxDecoration(
                      color: Is_User ? const Color(0xFF6200EE) : const Color(0x1AFFFFFF),
                      borderRadius: BorderRadius.only(
                        topLeft: const Radius.circular(16),
                        topRight: const Radius.circular(16),
                        bottomLeft: Is_User ? const Radius.circular(16) : Radius.zero,
                        bottomRight: Is_User ? Radius.zero : const Radius.circular(16),
                      ),
                      border: Border.all(
                        color: Is_User ? const Color(0x33FFFFFF) : const Color(0x13FFFFFF),
                        width: 1,
                      ),
                    ),
                    child: Text(
                      Msg['text'] ?? "",
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        height: 1.4,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: const BoxDecoration(
              color: Color(0xFF13111C),
              borderRadius: BorderRadius.only(
                topLeft: Radius.circular(24),
                topRight: Radius.circular(24),
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _Input_Controller,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: "Preguntar sobre chats o gastos...",
                      hintStyle: const TextStyle(color: Colors.white38),
                      filled: true,
                      fillColor: const Color(0x0DFFFFFF),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(30),
                        borderSide: BorderSide.none,
                      ),
                    ),
                    onSubmitted: (_) => _Send_Message(),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  decoration: const BoxDecoration(
                    color: Colors.cyanAccent,
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    icon: const Icon(Icons.send, color: Colors.black),
                    onPressed: _Send_Message,
                  ),
                )
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _Build_Service_Badge(String Label, bool Active, VoidCallback On_Tap) {
    return GestureDetector(
      onTap: On_Tap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: Active ? const Color(0x1A00F0FF) : const Color(0x1AFF0055),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: Active ? const Color(0xFF00F0FF) : const Color(0xFFFF0055),
            width: 1,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Active ? Icons.check_circle : Icons.warning_amber_rounded,
              color: Active ? Colors.cyanAccent : Colors.redAccent,
              size: 14,
            ),
            const SizedBox(width: 4),
            Text(
              Label,
              style: TextStyle(
                color: Active ? Colors.cyanAccent : Colors.redAccent,
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
