plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
}

android {
	namespace = "br.com.wellingtoncruz.notasworkflow"
	compileSdk = 35

	defaultConfig {
		applicationId = "br.com.wellingtoncruz.notasworkflow"
		minSdk = 26
		targetSdk = 35
		versionCode = 4
		versionName = "1.12.3"
	}

	signingConfigs {
		// A release key can come from the environment (GitHub secrets); otherwise the debug key signs it.
		val keystore = System.getenv("NW_KEYSTORE_FILE")
		if (keystore != null && file(keystore).exists()) {
			create("release") {
				storeFile = file(keystore)
				storePassword = System.getenv("NW_KEYSTORE_PASSWORD")
				keyAlias = System.getenv("NW_KEY_ALIAS")
				keyPassword = System.getenv("NW_KEY_PASSWORD")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
	buildFeatures {
		compose = true
	}
	testOptions {
		unitTests.isReturnDefaultValues = true
	}
}

dependencies {
	val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
	implementation(composeBom)
	implementation("androidx.core:core-ktx:1.15.0")
	implementation("androidx.activity:activity-compose:1.9.3")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.foundation:foundation")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-core")
	implementation("androidx.compose.ui:ui-tooling-preview")
	debugImplementation("androidx.compose.ui:ui-tooling")

	testImplementation("junit:junit:4.13.2")
}
