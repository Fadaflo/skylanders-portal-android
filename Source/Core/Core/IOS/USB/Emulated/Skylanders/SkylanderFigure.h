// Copyright 2023 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#pragma once

#include <array>
#include <optional>
#include <span>
#include <string>

#include "Common/CommonTypes.h"
#include "Common/IOFile.h"

namespace IOS::HLE::USB
{
enum class Type : u8;

struct SkylanderDateTime final
{
  u8 minute;
  u8 hour;
  u8 day;
  u8 month;
  u16 year;
};

struct SkylanderData final
{
  std::array<u8, 11> toy_code;
  u16 money;
  u16 hero_level;
  u32 playtime;
  // Null-terminated UTF-16 string
  std::array<u16, 0x10> nickname;
  SkylanderDateTime last_reset;
  SkylanderDateTime last_placed;
};

struct Trophydata final
{
  u8 unlocked_villains;
};

struct FigureData final
{
  Type normalized_type;
  u16 figure_id;
  u16 variant_id;
  union
  {
    SkylanderData skylander_data;
    Trophydata trophy_data;
  };
};

constexpr u32 BLOCK_COUNT = 0x40;
constexpr u32 BLOCK_SIZE = 0x10;
constexpr u32 FIGURE_SIZE = BLOCK_COUNT * BLOCK_SIZE;

class SkylanderFigure
{
public:
  SkylanderFigure(const std::string& file_path);
  SkylanderFigure(File::IOFile file);
  bool Create(u16 sky_id, u16 sky_var,
              std::optional<std::array<u8, 4>> requested_nuid = std::nullopt);
  void Save();
  void Close();
  bool FileIsOpen() const;
  void GetBlock(u8 index, u8* dest) const;
  void SetBlock(u8 block, const u8* buf);
  void DecryptFigure(std::array<u8, FIGURE_SIZE>* dest) const;
  FigureData GetData() const;
  void SetData(FigureData* data);

  // Die Bloecke, in denen der Spielstand liegt: zwei Bereiche zu elf Bloecken.
  // Sektorschluessel und Kopf gehoeren nicht dazu.
  static constexpr std::array<u8, 22> SAVE_BLOCKS = {8,  9,  10, 12, 13, 14, 16, 17,
                                                     18, 20, 21, 36, 37, 38, 40, 41,
                                                     42, 44, 45, 46, 48, 49};
  static constexpr u32 SAVE_BLOCKS_SIZE = 22 * BLOCK_SIZE;

  // Verschluesselt ein komplettes Klartextabbild und speichert es.
  //
  // Das Gegenstueck zu DecryptFigure. Zusammen erlauben die beiden, einen
  // Spielstand ausserhalb dieser Klasse zu bearbeiten -- gebraucht fuer die
  // "ultimativen" Figuren, deren Spielstand aus einer Vorlage stammt.
  void SetDecrypted(std::span<const u8, FIGURE_SIZE> decrypted);

private:
  void PopulateSectorTrailers();
  void PopulateKeys();
  void GenerateIncompleteHashIn(u8* dest) const;
  void Encrypt(std::span<const u8, FIGURE_SIZE>);

  File::IOFile m_sky_file;
  std::array<u8, FIGURE_SIZE> m_data;
};
}  // namespace IOS::HLE::USB
