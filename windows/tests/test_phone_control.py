"""Regression tests for Windows/Android volume and the opt-in control bridge."""
import unittest
import time
from phone_control_bridge import PhoneControlBridge
from audio_control import normalize_percent


class ControlTests(unittest.TestCase):
    def test_percent_validation(self):
        self.assertEqual(normalize_percent(0),0.0)
        self.assertEqual(normalize_percent(75),0.75)
        self.assertEqual(normalize_percent(100),1.0)
        for bad in (-1,101,999):
            with self.assertRaises(ValueError):
                normalize_percent(bad)

    def test_not_enabled_cannot_receive_commands(self):
        bridge=PhoneControlBridge()
        with self.assertRaises(RuntimeError):
            bridge.enqueue("volume",44)
        bridge.update_state(42,False,True)
        bridge.enqueue("volume",44)
        bridge.enqueue("mute",True)
        self.assertEqual(bridge.next(),{"action":"volume","value":44})
        self.assertEqual(bridge.next(),{"action":"mute","value":True})
        self.assertEqual(bridge.next(),{"action":"none"})
        bridge.update_state(42,True,False)
        with self.assertRaises(RuntimeError):
            bridge.enqueue("volume",22)

    def test_reject_unexpected_or_invalid_commands(self):
        bridge=PhoneControlBridge()
        bridge.update_state(50,False,True)
        for kind,value in (("touch","tap"),("volume",False),("volume",-1),
                           ("volume",101),("mute","yes"),("shutdown",True)):
            with self.assertRaises(ValueError):
                bridge.enqueue(kind,value)

    def test_notifications_capped(self):
        bridge=PhoneControlBridge()
        for n in range(50):
            bridge.add_notice("Title "+str(n),"some text","app")
        self.assertEqual(len(bridge.status()["notifications"]),30)
        bridge.add_notice("T"*600,"D"*600,"app")
        newest=bridge.status()["notifications"][0]
        self.assertLessEqual(len(newest["title"]),160)
        self.assertLessEqual(len(newest["text"]),160)


if __name__ == "__main__":
    unittest.main()
