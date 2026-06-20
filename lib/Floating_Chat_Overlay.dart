import 'dart:async';
import 'package:flutter/material.dart';
import 'Native_Bridge_Service.dart';

class Floating_Chat_Overlay extends StatefulWidget {
  const Floating_Chat_Overlay({Key? Key_Val}) : super(key: Key_Val);

  @override
  State<Floating_Chat_Overlay> createState() => _Floating_Chat_Overlay_State();
}

class _Floating_Chat_Overlay_State extends State<Floating_Chat_Overlay> {
  final TextEditingController _Input_Controller = TextEditingController();
  final List<Map<String, String>> _Messages = [];
  bool _Is_Generating = false;
  StreamSubscription<String>? _Inference_Subscription;

  @override
  void initState() {
    super.initState();
    _Messages.add({
      'sender': 'agent',
      'text': '¡Hola! Detecté actividad relevante. ¿En qué puedo ayudarte?',
    });
  }

  @override
  void dispose() {
    _Inference_Subscription?.cancel();
    _Input_Controller.dispose();
    super.dispose();
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

    final List<dynamic> Context_Matches = await Native_Bridge_Service.Search_Vector_Logs(Prompt, Limit: 2);
    
    String Injection_Context = "";
    if (Context_Matches.isNotEmpty) {
      Injection_Context = "\nContexto Recuperado:\n" + Context_Matches.map((M) {
        return "- App: ${M['title']} (${M['sourceApp']}). Log: ${M['rawText']}";
      }).join("\n");
    }

    final String Full_Prompt = "SYSTEM: Eres un asistente personal local en Android. Usa este contexto si es relevante: $Injection_Context\nUSER: $Prompt\nASSISTANT:";

    _Inference_Subscription?.cancel();
    _Inference_Subscription = Native_Bridge_Service.Listen_To_Inference().listen((Token) {
      setState(() {
        if (_Messages.isNotEmpty) {
          final int Last_Index = _Messages.length - 1;
          _Messages[Last_Index]['text'] = (_Messages[Last_Index]['text'] ?? "") + Token;
        }
      });
    }, onDone: () {
      setState(() {
        _Is_Generating = false;
      });
    }, onError: (Error) {
      setState(() {
        _Is_Generating = false;
      });
    });

    await Native_Bridge_Service.Run_Inference(Full_Prompt);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Container(
        decoration: BoxDecoration(
          color: const Color(0xE6121214),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0x33FFFFFF), width: 1.5),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.5),
              blurRadius: 10,
              spreadRadius: 2,
            )
          ],
        ),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: const BoxDecoration(
                color: Color(0x1AFFFFFF),
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(16),
                  topRight: Radius.circular(16),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: Colors.cyan,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Text(
                        "AI Personal Overlay",
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white70, size: 16),
                    onPressed: () {
                      Native_Bridge_Service.Hide_Overlay_Bubble();
                    },
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.all(8),
                itemCount: _Messages.length,
                itemBuilder: (Ctx, Idx) {
                  final Msg = _Messages[Idx];
                  final Is_User = Msg['sender'] == 'user';
                  return Align(
                    alignment: Is_User ? Alignment.centerRight : Alignment.centerLeft,
                    child: Container(
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: Is_User ? const Color(0xFF007ACC) : const Color(0x33FFFFFF),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        Msg['text'] ?? "",
                        style: const TextStyle(color: Colors.white, fontSize: 11),
                      ),
                    ),
                  );
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _Input_Controller,
                      style: const TextStyle(color: Colors.white, fontSize: 11),
                      decoration: InputDecoration(
                        hintText: "Preguntar...",
                        hintStyle: const TextStyle(color: Colors.white54, fontSize: 11),
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                        filled: true,
                        fillColor: const Color(0x1AFFFFFF),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 4),
                  IconButton(
                    icon: const Icon(Icons.send, color: Colors.cyan, size: 16),
                    onPressed: _Send_Message,
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                  )
                ],
              ),
            )
          ],
        ),
      ),
    );
  }
}
