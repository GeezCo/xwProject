# -*- coding: utf-8 -*-
"""
V3 event extractor: preprocess text to remove all types of quotes
before sending to LLM, preventing unescaped quotes in JSON output.

Inherits from LLMEventExtractor, only overrides text preprocessing.
"""
import re
from llm_event_extractor import LLMEventExtractor


class LLMEventExtractorV3(LLMEventExtractor):
    """V3: input preprocessing - remove quotes before LLM call"""

    @staticmethod
    def preprocess_text(text: str) -> str:
        # Step 1: unescape \" to "
        text = text.replace('\\"', '"')

        # Step 2: normalize Chinese quotes to ASCII "
        text = text.replace('“', '"').replace('”', '"')

        # Step 3: remove paired "xxx" (1-30 chars inside), keep content
        text = re.sub(r'"([^"]{1,30})"', r'\1', text)

        # Step 4: remove any remaining stray "
        text = text.replace('"', '')

        return text

    def extract_events_from_paragraph(self, paragraph: str, paragraph_id: int):
        """Override: preprocess text, then call LLM"""
        cleaned = self.preprocess_text(paragraph)

        print(f"Processing paragraph {paragraph_id + 1}...")
        if cleaned != paragraph:
            print(f"  [V3] original: {paragraph[:80]}...")
            print(f"  [V3] cleaned:  {cleaned[:80]}...")

        response = self.call_llm(cleaned)
        if response is None:
            return []

        events = self.parse_llm_response(response, cleaned)

        for event in events:
            event['paragraph_id'] = paragraph_id

        print(f"Paragraph {paragraph_id + 1}: {len(events)} events")
        return events
