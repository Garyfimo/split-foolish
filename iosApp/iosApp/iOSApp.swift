import SwiftUI
import FirebaseCore

/// Initialise the Firebase iOS SDK before any Swift or Kotlin code touches `Firebase.auth`.
/// Reads configuration from `GoogleService-Info.plist` (must be in the app bundle; added to the
/// iosApp target in Xcode). Without this call, the first `Firebase.auth.currentUser` read from
/// the shared Kotlin framework crashes with "The default FirebaseApp is not initialized."
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
