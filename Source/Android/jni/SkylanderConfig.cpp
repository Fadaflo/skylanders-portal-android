// Copyright 2023 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <jni.h>

#include <array>
#include <memory>
#include <vector>

#include "AndroidCommon/AndroidCommon.h"
#include "AndroidCommon/IDCache.h"
#include "Core/IOS/USB/Emulated/Skylanders/Skylander.h"
#include "Core/IOS/USB/Emulated/Skylanders/SkylanderCrypto.h"
#include "Core/IOS/USB/Emulated/Skylanders/SkylanderFigure.h"
#include "Core/System.h"

extern "C" {

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_getSkylanderMap(JNIEnv* env,
                                                                                   jclass clazz)
{
  jobject hash_map_obj = env->NewObject(IDCache::GetHashMapClass(), IDCache::GetHashMapInit(),
                                        static_cast<u16>(IOS::HLE::USB::list_skylanders.size()));

  jclass skylander_class =
      env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/model/SkylanderPair");

  jmethodID skylander_init = env->GetMethodID(skylander_class, "<init>", "(II)V");

  for (const auto& it : IOS::HLE::USB::list_skylanders)
  {
    const std::string& name = it.second.name;
    jobject skylander_obj =
        env->NewObject(skylander_class, skylander_init, it.first.first, it.first.second);
    env->CallObjectMethod(hash_map_obj, IDCache::GetHashMapPut(), skylander_obj,
                          ToJString(env, name));
    env->DeleteLocalRef(skylander_obj);
  }

  return hash_map_obj;
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_getInverseSkylanderMap(
    JNIEnv* env, jclass clazz)
{
  jobject hash_map_obj = env->NewObject(IDCache::GetHashMapClass(), IDCache::GetHashMapInit(),
                                        static_cast<u16>(IOS::HLE::USB::list_skylanders.size()));

  jclass skylander_class =
      env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/model/SkylanderPair");

  jmethodID skylander_init = env->GetMethodID(skylander_class, "<init>", "(II)V");

  for (const auto& it : IOS::HLE::USB::list_skylanders)
  {
    const std::string& name = it.second.name;
    jobject skylander_obj =
        env->NewObject(skylander_class, skylander_init, it.first.first, it.first.second);
    env->CallObjectMethod(hash_map_obj, IDCache::GetHashMapPut(), ToJString(env, name),
                          skylander_obj);
    env->DeleteLocalRef(skylander_obj);
  }

  return hash_map_obj;
}

JNIEXPORT jboolean JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_removeSkylander(JNIEnv* env,
                                                                                   jclass clazz,
                                                                                   jint slot)
{
  auto& system = Core::System::GetInstance();
  return static_cast<jboolean>(system.GetSkylanderPortal().RemoveSkylander(slot));
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_loadSkylander(JNIEnv* env,
                                                                                 jclass clazz,
                                                                                 jint slot,
                                                                                 jstring file_name)
{
  File::IOFile sky_file(GetJString(env, file_name), "r+b");
  if (!sky_file)
  {
    return nullptr;
  }
  std::array<u8, 0x40 * 0x10> file_data;
  if (!sky_file.ReadBytes(file_data.data(), file_data.size()))
  {
    return nullptr;
  }

  jclass pair_class = env->FindClass("android/util/Pair");
  jmethodID pair_init =
      env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  jclass integer_class = env->FindClass("java/lang/Integer");
  jmethodID int_init = env->GetMethodID(integer_class, "<init>", "(I)V");

  auto& system = Core::System::GetInstance();
  system.GetSkylanderPortal().RemoveSkylander(slot);
  std::string name = "Unknown";

  std::pair<u16, u16> id_var = system.GetSkylanderPortal().CalculateIDs(file_data);

  const auto it = IOS::HLE::USB::list_skylanders.find(std::make_pair(id_var.first, id_var.second));

  if (it != IOS::HLE::USB::list_skylanders.end())
  {
    name = it->second.name;
  }

  return env->NewObject(
      pair_class, pair_init,
      env->NewObject(integer_class, int_init,
                     system.GetSkylanderPortal().LoadSkylander(
                         std::make_unique<IOS::HLE::USB::SkylanderFigure>(std::move(sky_file)))),
      ToJString(env, name));
}

// Read the selected upgrade path without loading the figure into a portal slot.
// -1 means unreadable, 0 means no path, 1 upper, 2 lower.
JNIEXPORT jint JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_readUpgradePath(
    JNIEnv* env, jclass clazz, jstring file_name)
{
  File::IOFile sky_file(GetJString(env, file_name), "rb");
  if (!sky_file)
    return -1;
  std::array<u8, IOS::HLE::USB::FIGURE_SIZE> file_data{};
  if (!sky_file.ReadBytes(file_data.data(), file_data.size()))
    return -1;

  IOS::HLE::USB::SkylanderFigure figure(std::move(sky_file));
  std::array<u8, IOS::HLE::USB::FIGURE_SIZE> decrypted{};
  figure.DecryptFigure(&decrypted);
  const u16 area = (static_cast<u8>(decrypted[0x89] + 1) != decrypted[0x249]) ? 0x80 : 0x240;
  const u16 upgrades = static_cast<u16>(decrypted[area + 0x10]) |
                       (static_cast<u16>(decrypted[area + 0x11]) << 8);
  if (!(upgrades & 1))
    return 0;
  return (upgrades & 2) ? 2 : 1;
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_createMaxedSkylander(
    JNIEnv* env, jclass clazz, jint id, jint var, jstring fileName, jint slot,
    jbyteArray plaintext, jint upgrades_word, jint money, jint hero_level)
{
  // Erschafft eine fertig ausgebaute Figur.
  //
  // Der Spielstand kommt als Klartext aus einer Vorlage -- der Abzug einer im
  // Spiel voll ausgebauten Figur. Weil die drei Pruefsummen des Kopfblocks im
  // Klartext stehen und mitkopiert werden, bleiben sie gueltig.
  //
  // Gold und Stufe werden danach ueber SetData gesetzt. Dolphin rechnet die
  // Pruefsummen dabei neu -- genau das, woran das PS3-Projekt gescheitert ist,
  // wo das Gold auf den Werten der Vorlage bleiben musste.
  const jsize length = env->GetArrayLength(plaintext);
  if (length != static_cast<jsize>(IOS::HLE::USB::SkylanderFigure::SAVE_BLOCKS_SIZE))
  {
    return nullptr;
  }

  std::vector<u8> plain(static_cast<size_t>(length));
  env->GetByteArrayRegion(plaintext, 0, length, reinterpret_cast<jbyte*>(plain.data()));

  auto figure = std::make_unique<IOS::HLE::USB::SkylanderFigure>(GetJString(env, fileName));
  if (!figure->Create(static_cast<u16>(id), static_cast<u16>(var)))
  {
    return nullptr;
  }

  std::array<u8, IOS::HLE::USB::FIGURE_SIZE> decrypted = {};
  figure->DecryptFigure(&decrypted);

  // Den Spielstand der Vorlage einsetzen.
  const auto& blocks = IOS::HLE::USB::SkylanderFigure::SAVE_BLOCKS;
  for (size_t i = 0; i < blocks.size(); ++i)
  {
    memcpy(decrypted.data() + blocks[i] * IOS::HLE::USB::BLOCK_SIZE,
           plain.data() + i * IOS::HLE::USB::BLOCK_SIZE, IOS::HLE::USB::BLOCK_SIZE);
  }

  // Beide Speicherbereiche gleich behandeln: Ausbauwort setzen, Spitzname
  // leeren.
  //
  // Im Ausbauwort sind Bit 0 und 1 der Pfad, 2 bis 8 die Faehigkeiten und 9 der
  // Seelenstein. Bits 10 und 11 sind unerklaert; sie bleiben null.
  //
  // Der Hut steckt nicht im Ausbauwort -- am Geraet gemessen: eine Figur aus
  // der unteren Vorlage kam mit Hosen-Hut, obwohl beide Bits null waren.
  // Deshalb wird fuer beide Pfade dieselbe hutfreie Vorlage benutzt und nur das
  // Pfad-Bit umgesetzt.
  for (const u16 area : {0x80, 0x240})
  {
    if (upgrades_word >= 0)
    {
      const u16 upgrades = static_cast<u16>(upgrades_word);
      memcpy(decrypted.data() + area + 0x10, &upgrades, sizeof(upgrades));
    }

    // Der Spitzname liegt in zwei Haelften zu 16 Byte.
    memset(decrypted.data() + area + 0x20, 0, 0x10);
    memset(decrypted.data() + area + 0x40, 0, 0x10);

    // Dieselbe Reihenfolge wie in SkylanderFigure::SetData.
    IOS::HLE::USB::SkylanderCrypto::ComputeChecksumType3(decrypted.data() + area + 0x50,
                                        decrypted.data() + area + 0xA);
    IOS::HLE::USB::SkylanderCrypto::ComputeChecksumType2(decrypted.data() + area + 0x10,
                                        decrypted.data() + area + 0xC);
    IOS::HLE::USB::SkylanderCrypto::ComputeChecksumType1(decrypted.data() + area, decrypted.data() + area + 0xE);
  }

  for (const u16 area : {0x110, 0x2D0})
  {
    IOS::HLE::USB::SkylanderCrypto::ComputeChecksumType6(decrypted.data() + area, decrypted.data() + area);
  }

  figure->SetDecrypted(decrypted);

  if (money >= 0 || hero_level >= 0)
  {
    IOS::HLE::USB::FigureData data = figure->GetData();
    if (data.normalized_type == IOS::HLE::USB::Type::Skylander)
    {
      if (money >= 0)
        data.skylander_data.money = static_cast<u16>(money);
      if (hero_level >= 0)
        data.skylander_data.hero_level = static_cast<u16>(hero_level);
      figure->SetData(&data);
    }
  }

  jclass pair_class = env->FindClass("android/util/Pair");
  jmethodID pair_init =
      env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  jclass integer_class = env->FindClass("java/lang/Integer");
  jmethodID int_init = env->GetMethodID(integer_class, "<init>", "(I)V");

  auto& system = Core::System::GetInstance();
  system.GetSkylanderPortal().RemoveSkylander(slot);

  std::string name = "Unknown";
  const auto it = IOS::HLE::USB::list_skylanders.find(
      std::make_pair(static_cast<u16>(id), static_cast<u16>(var)));
  if (it != IOS::HLE::USB::list_skylanders.end())
  {
    name = it->second.name;
  }

  return env->NewObject(
      pair_class, pair_init,
      env->NewObject(integer_class, int_init,
                     system.GetSkylanderPortal().LoadSkylander(std::move(figure))),
      ToJString(env, name));
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_createSkylander(
    JNIEnv* env, jclass clazz, jint id, jint var, jstring fileName, jint slot)
{
  u16 sky_id = static_cast<u16>(id);
  u16 sky_var = static_cast<u16>(var);

  std::string file_name = GetJString(env, fileName);

  auto& system = Core::System::GetInstance();
  {
    IOS::HLE::USB::SkylanderFigure figure(file_name);
    figure.Create(sky_id, sky_var);
    figure.Close();
  }
  system.GetSkylanderPortal().RemoveSkylander(slot);

  jclass pair_class = env->FindClass("android/util/Pair");
  jmethodID pair_init =
      env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");

  jclass integer_class = env->FindClass("java/lang/Integer");
  jmethodID integer_init = env->GetMethodID(integer_class, "<init>", "(I)V");

  File::IOFile sky_file(file_name, "r+b");
  if (!sky_file)
  {
    return nullptr;
  }
  std::array<u8, 0x40 * 0x10> file_data;
  if (!sky_file.ReadBytes(file_data.data(), file_data.size()))
  {
    return nullptr;
  }

  std::string name = "Unknown";

  const auto it = IOS::HLE::USB::list_skylanders.find(std::make_pair(sky_id, sky_var));

  if (it != IOS::HLE::USB::list_skylanders.end())
  {
    name = it->second.name;
  }

  return env->NewObject(
      pair_class, pair_init,
      env->NewObject(integer_class, integer_init,
                     system.GetSkylanderPortal().LoadSkylander(
                         std::make_unique<IOS::HLE::USB::SkylanderFigure>(std::move(sky_file)))),
      ToJString(env, name));
}
}
