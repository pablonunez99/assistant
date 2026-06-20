import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:local_personal_ai_agent/main.dart';

void main() {
  testWidgets('App smoke test', (WidgetTester Tester) async {
    await Tester.pumpWidget(const Local_Ai_Agent_App());
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
