import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from matplotlib import ticker

""" 0. import data """
# trip id generated from input plans (no flood)
nanjing_tripId_baseline_df = pd.read_csv("Your folder path\\tripIdFromPopulationBase.csv")
hamburg_tripId_baseline_df = pd.read_csv("Your folder path\\tripIdFromPopulationBase.csv")
la_tripId_baseline_df = pd.read_csv("Your folder path\\tripIdFromPopulationBase.csv")

# trip id generated from flooding input plans (flooding scenarios)
nanjing_input_tripId_t3_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold3.0.csv")
nanjing_input_tripId_t4_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold4.0.csv")
nanjing_input_tripId_t5_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold5.0.csv")
hamburg_input_tripId_t4_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold4.0.csv")
hamburg_input_tripId_t5_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold5.0.csv")
hamburg_input_tripId_t6_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold6.0.csv")
la_input_tripId_t0_07_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold0.07.csv")
la_input_tripId_t0_3_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold0.3.csv")
la_input_tripId_t0_6_df = pd.read_csv("Your folder path\\tripIdFromPopulationThreshold0.6.csv")

""" 1. Inaccessible trip ID due to floods """
def get_flood_induced_trip_ids(tripId_baseline_df, tripId_input_flooding_df):
    tripId_baseline_df['test'] = 1
    tripId_input_flooding_df['test'] = 1
    flood_induced_df = pd.merge(how='outer', left=tripId_baseline_df, right=tripId_input_flooding_df, on='tripId')
    flood_induced_df = flood_induced_df[flood_induced_df['test_y'].isnull()]
    return set(flood_induced_df['tripId'])

# for nanjing
inaccessible_tripIds_nanjing_t3 = get_flood_induced_trip_ids(nanjing_tripId_baseline_df, nanjing_input_tripId_t3_df)
inaccessible_tripIds_nanjing_t4 = get_flood_induced_trip_ids(nanjing_tripId_baseline_df, nanjing_input_tripId_t4_df)
inaccessible_tripIds_nanjing_t5 = get_flood_induced_trip_ids(nanjing_tripId_baseline_df, nanjing_input_tripId_t5_df)
# for hamburg
inaccessible_tripIds_hamburg_t4 = get_flood_induced_trip_ids(hamburg_tripId_baseline_df, hamburg_input_tripId_t4_df)
inaccessible_tripIds_hamburg_t5 = get_flood_induced_trip_ids(hamburg_tripId_baseline_df, hamburg_input_tripId_t5_df)
inaccessible_tripIds_hamburg_t6 = get_flood_induced_trip_ids(hamburg_tripId_baseline_df, hamburg_input_tripId_t6_df)
# for la
inaccessible_tripIds_la_t007 = get_flood_induced_trip_ids(la_tripId_baseline_df, la_input_tripId_t0_07_df)
inaccessible_tripIds_la_t03 = get_flood_induced_trip_ids(la_tripId_baseline_df, la_input_tripId_t0_3_df)
inaccessible_tripIds_la_t06 = get_flood_induced_trip_ids(la_tripId_baseline_df, la_input_tripId_t0_6_df)

""" 2. Unacceptable trip Id (travel time > 300min) """
def get_duration_induced_trip_ids(filePath):
    df = pd.read_csv(filePath, sep=";", compression="gzip")
    df = df[['travel_time(min)', 'person', 'trip_id']]
    unacceptable_tripIds = set(df[df['travel_time(min)'] > 300]['trip_id'])
    return unacceptable_tripIds

# for nanjing
unacceptable_tripIds_nanjing_t3_fa = get_duration_induced_trip_ids(r"Your folder path\threshold3.0mA\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t4_fa = get_duration_induced_trip_ids(r"Your folder path\threshold4.0mA\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t5_fa = get_duration_induced_trip_ids(r"Your folder path\threshold5.0mA\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t3_fb = get_duration_induced_trip_ids(r"Your folder path\threshold3.0mB\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t4_fb = get_duration_induced_trip_ids(r"Your folder path\threshold4.0mB\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t5_fb = get_duration_induced_trip_ids(r"Your folder path\threshold5.0mB\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t3_f = get_duration_induced_trip_ids(r"Your folder path\threshold3.0m\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t4_f = get_duration_induced_trip_ids(r"Your folder path\threshold4.0m\output_trips_wgs84.csv.gz")
unacceptable_tripIds_nanjing_t5_f = get_duration_induced_trip_ids(r"Your folder path\threshold5.0m\output_trips_wgs84.csv.gz")

# for hamburg
unacceptable_tripIds_hamburg_t4_fa = get_duration_induced_trip_ids(r"Your folder path\threshold4A\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t5_fa = get_duration_induced_trip_ids(r"Your folder path\threshold5A\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t6_fa = get_duration_induced_trip_ids(r"Your folder path\threshold6A\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t4_fb = get_duration_induced_trip_ids(r"Your folder path\threshold4B\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t5_fb = get_duration_induced_trip_ids(r"Your folder path\threshold5B\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t6_fb = get_duration_induced_trip_ids(r"Your folder path\threshold6B\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t4_f = get_duration_induced_trip_ids(r"Your folder path\threshold4\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t5_f = get_duration_induced_trip_ids(r"Your folder path\threshold5\output_trips_wgs84.csv.gz")
unacceptable_tripIds_hamburg_t6_f = get_duration_induced_trip_ids(r"Your folder path\threshold6\output_trips_wgs84.csv.gz")

# for la
unacceptable_tripIds_la_t007_fa = get_duration_induced_trip_ids(r"Your folder path\threshold0.07A\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t03_fa = get_duration_induced_trip_ids(r"Your folder path\threshold0.3A\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t06_fa = get_duration_induced_trip_ids(r"Your folder path\threshold0.6A\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t007_fb = get_duration_induced_trip_ids(r"Your folder path\threshold0.07B\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t03_fb = get_duration_induced_trip_ids(r"Your folder path\threshold0.3B\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t06_fb = get_duration_induced_trip_ids(r"Your folder path\threshold0.6B\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t007_f = get_duration_induced_trip_ids(r"Your folder path\threshold0.07\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t03_f = get_duration_induced_trip_ids(r"Your folder path\threshold0.3\output_trips_wgs84.csv.gz")
unacceptable_tripIds_la_t06_f = get_duration_induced_trip_ids(r"Your folder path\threshold0.6\output_trips_wgs84.csv.gz")

""" 3. car trip Id and pt trip Id """
def getCarPtTripIds(filePath):
    df = pd.read_csv(filePath, sep=";")
    car_trip_ids = set(df[df["my_modes"] == 'car']['trip_id'])
    pt_trip_ids = set(df[df["my_modes"] == 'pt']['trip_id'])
    return car_trip_ids, pt_trip_ids

# for nanjing
car_trip_ids_nanjing_t3_fa, pt_trip_ids_nanjing_t3_fa = getCarPtTripIds(r"Your folder path\threshold3.0mA\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t4_fa, pt_trip_ids_nanjing_t4_fa = getCarPtTripIds(r"Your folder path\threshold4.0mA\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t5_fa, pt_trip_ids_nanjing_t5_fa = getCarPtTripIds(r"Your folder path\threshold5.0mA\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t3_fb, pt_trip_ids_nanjing_t3_fb = getCarPtTripIds(r"Your folder path\threshold3.0mB\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t4_fb, pt_trip_ids_nanjing_t4_fb = getCarPtTripIds(r"Your folder path\threshold4.0mB\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t5_fb, pt_trip_ids_nanjing_t5_fb = getCarPtTripIds(r"Your folder path\threshold5.0mB\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t3_f, pt_trip_ids_nanjing_t3_f = getCarPtTripIds(r"Your folder path\threshold3.0m\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t4_f, pt_trip_ids_nanjing_t4_f = getCarPtTripIds(r"Your folder path\threshold4.0m\output_trips_wgs84_cleaned.csv")
car_trip_ids_nanjing_t5_f, pt_trip_ids_nanjing_t5_f = getCarPtTripIds(r"Your folder path\threshold5.0m\output_trips_wgs84_cleaned.csv")

# for hamburg
car_trip_ids_hamburg_t4_fa, pt_trip_ids_hamburg_t4_fa = getCarPtTripIds(r"Your folder path\threshold4A\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t5_fa, pt_trip_ids_hamburg_t5_fa = getCarPtTripIds(r"Your folder path\threshold5A\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t6_fa, pt_trip_ids_hamburg_t6_fa = getCarPtTripIds(r"Your folder path\threshold6A\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t4_fb, pt_trip_ids_hamburg_t4_fb = getCarPtTripIds(r"Your folder path\threshold4B\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t5_fb, pt_trip_ids_hamburg_t5_fb = getCarPtTripIds(r"Your folder path\threshold5B\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t6_fb, pt_trip_ids_hamburg_t6_fb = getCarPtTripIds(r"Your folder path\threshold6B\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t4_f, pt_trip_ids_hamburg_t4_f = getCarPtTripIds(r"Your folder path\threshold4\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t5_f, pt_trip_ids_hamburg_t5_f = getCarPtTripIds(r"Your folder path\threshold5\output_trips_wgs84_cleaned.csv")
car_trip_ids_hamburg_t6_f, pt_trip_ids_hamburg_t6_f = getCarPtTripIds(r"Your folder path\threshold6\output_trips_wgs84_cleaned.csv")

# for la
car_trip_ids_la_t007_fa, pt_trip_ids_la_t007_fa = getCarPtTripIds(r"Your folder path\threshold0.07A\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t03_fa, pt_trip_ids_la_t03_fa = getCarPtTripIds(r"Your folder path\threshold0.3A\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t06_fa, pt_trip_ids_la_t06_fa = getCarPtTripIds(r"Your folder path\threshold0.6A\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t007_fb, pt_trip_ids_la_t007_fb = getCarPtTripIds(r"Your folder path\threshold0.07B\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t03_fb, pt_trip_ids_la_t03_fb = getCarPtTripIds(r"Your folder path\threshold0.3B\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t06_fb, pt_trip_ids_la_t06_fb = getCarPtTripIds(r"Your folder path\threshold0.6B\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t007_f, pt_trip_ids_la_t007_f = getCarPtTripIds(r"Your folder path\threshold0.07\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t03_f, pt_trip_ids_la_t03_f = getCarPtTripIds(r"Your folder path\threshold0.3\output_trips_wgs84_cleaned.csv")
car_trip_ids_la_t06_f, pt_trip_ids_la_t06_f = getCarPtTripIds(r"Your folder path\threshold0.6\output_trips_wgs84_cleaned.csv")

""" 4. unreached trip ids """
nanjing_trip_ids_baseline = set(nanjing_tripId_baseline_df['tripId'])
hamburg_trip_ids_baseline = set(hamburg_tripId_baseline_df['tripId'])
la_trip_ids_baseline = set(la_tripId_baseline_df['tripId'])
# for nanjing
unreached_trip_ids_nanjing_t3_fa = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t3 - unacceptable_tripIds_nanjing_t3_fa - car_trip_ids_nanjing_t3_fa - pt_trip_ids_nanjing_t3_fa
unreached_trip_ids_nanjing_t4_fa = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t4 - unacceptable_tripIds_nanjing_t4_fa - car_trip_ids_nanjing_t4_fa - pt_trip_ids_nanjing_t4_fa
unreached_trip_ids_nanjing_t5_fa = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t5 - unacceptable_tripIds_nanjing_t5_fa - car_trip_ids_nanjing_t5_fa - pt_trip_ids_nanjing_t5_fa
unreached_trip_ids_nanjing_t3_fb = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t3 - unacceptable_tripIds_nanjing_t3_fb - car_trip_ids_nanjing_t3_fb - pt_trip_ids_nanjing_t3_fb
unreached_trip_ids_nanjing_t4_fb = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t4 - unacceptable_tripIds_nanjing_t4_fb - car_trip_ids_nanjing_t4_fb - pt_trip_ids_nanjing_t4_fb
unreached_trip_ids_nanjing_t5_fb = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t5 - unacceptable_tripIds_nanjing_t5_fb - car_trip_ids_nanjing_t5_fb - pt_trip_ids_nanjing_t5_fb
unreached_trip_ids_nanjing_t3_f = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t3 - unacceptable_tripIds_nanjing_t3_f - car_trip_ids_nanjing_t3_f - pt_trip_ids_nanjing_t3_f
unreached_trip_ids_nanjing_t4_f = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t4 - unacceptable_tripIds_nanjing_t4_f - car_trip_ids_nanjing_t4_f - pt_trip_ids_nanjing_t4_f
unreached_trip_ids_nanjing_t5_f = nanjing_trip_ids_baseline - inaccessible_tripIds_nanjing_t5 - unacceptable_tripIds_nanjing_t5_f - car_trip_ids_nanjing_t5_f - pt_trip_ids_nanjing_t5_f

# for hamburg
unreached_trip_ids_hamburg_t4_fa = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t4 - unacceptable_tripIds_hamburg_t4_fa - car_trip_ids_hamburg_t4_fa - pt_trip_ids_hamburg_t4_fa
unreached_trip_ids_hamburg_t5_fa = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t5 - unacceptable_tripIds_hamburg_t5_fa - car_trip_ids_hamburg_t5_fa - pt_trip_ids_hamburg_t5_fa
unreached_trip_ids_hamburg_t6_fa = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t6 - unacceptable_tripIds_hamburg_t6_fa - car_trip_ids_hamburg_t6_fa - pt_trip_ids_hamburg_t6_fa
unreached_trip_ids_hamburg_t4_fb = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t4 - unacceptable_tripIds_hamburg_t4_fb - car_trip_ids_hamburg_t4_fb - pt_trip_ids_hamburg_t4_fb
unreached_trip_ids_hamburg_t5_fb = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t5 - unacceptable_tripIds_hamburg_t5_fb - car_trip_ids_hamburg_t5_fb - pt_trip_ids_hamburg_t5_fb
unreached_trip_ids_hamburg_t6_fb = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t6 - unacceptable_tripIds_hamburg_t6_fb - car_trip_ids_hamburg_t6_fb - pt_trip_ids_hamburg_t6_fb
unreached_trip_ids_hamburg_t4_f = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t4 - unacceptable_tripIds_hamburg_t4_f - car_trip_ids_hamburg_t4_f - pt_trip_ids_hamburg_t4_f
unreached_trip_ids_hamburg_t5_f = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t5 - unacceptable_tripIds_hamburg_t5_f - car_trip_ids_hamburg_t5_f - pt_trip_ids_hamburg_t5_f
unreached_trip_ids_hamburg_t6_f = hamburg_trip_ids_baseline - inaccessible_tripIds_hamburg_t6 - unacceptable_tripIds_hamburg_t6_f - car_trip_ids_hamburg_t6_f - pt_trip_ids_hamburg_t6_f

# for la
unreached_trip_ids_la_t007_fa = la_trip_ids_baseline - inaccessible_tripIds_la_t007 - unacceptable_tripIds_la_t007_fa - car_trip_ids_la_t007_fa - pt_trip_ids_la_t007_fa
unreached_trip_ids_la_t03_fa = la_trip_ids_baseline - inaccessible_tripIds_la_t03 - unacceptable_tripIds_la_t03_fa - car_trip_ids_la_t03_fa - pt_trip_ids_la_t03_fa
unreached_trip_ids_la_t06_fa = la_trip_ids_baseline - inaccessible_tripIds_la_t06 - unacceptable_tripIds_la_t06_fa - car_trip_ids_la_t06_fa - pt_trip_ids_la_t06_fa
unreached_trip_ids_la_t007_fb = la_trip_ids_baseline - inaccessible_tripIds_la_t007 - unacceptable_tripIds_la_t007_fb - car_trip_ids_la_t007_fb - pt_trip_ids_la_t007_fb
unreached_trip_ids_la_t03_fb = la_trip_ids_baseline - inaccessible_tripIds_la_t03 - unacceptable_tripIds_la_t03_fb - car_trip_ids_la_t03_fb - pt_trip_ids_la_t03_fb
unreached_trip_ids_la_t06_fb = la_trip_ids_baseline - inaccessible_tripIds_la_t06 - unacceptable_tripIds_la_t06_fb - car_trip_ids_la_t06_fb - pt_trip_ids_la_t06_fb
unreached_trip_ids_la_t007_f = la_trip_ids_baseline - inaccessible_tripIds_la_t007 - unacceptable_tripIds_la_t007_f - car_trip_ids_la_t007_f - pt_trip_ids_la_t007_f
unreached_trip_ids_la_t03_f = la_trip_ids_baseline - inaccessible_tripIds_la_t03 - unacceptable_tripIds_la_t03_f - car_trip_ids_la_t03_f - pt_trip_ids_la_t03_f
unreached_trip_ids_la_t06_f = la_trip_ids_baseline - inaccessible_tripIds_la_t06 - unacceptable_tripIds_la_t06_f - car_trip_ids_la_t06_f - pt_trip_ids_la_t06_f

""" 5. calculate trip flow """
def plot_sankey_opt(trip_ids_list_fa, trip_ids_list_fb, trip_ids_list_f, out_file_path):
    """ 
    trip_ids_list_fa: inlcudes Inaccessible, unreached, unacceptable, pt, car 的 trip set
    """
    links = []
    # Fa --> Fb   
    print("/* from Fa (marked as 'xxx') to Fb (marked as 'xxx ')*/")
    print(["Inaccessible trips",  " ",  len(trip_ids_list_fa[0] & trip_ids_list_fb[0])], ",")
    for fa_set, name1 in zip(trip_ids_list_fa[1:], ['Unreached trips', 'Unaccepted trips', 'PT trips', 'Car trips']):
        for fb_set, name2 in zip(trip_ids_list_fb[1:], ["   ", "    ", "     ", "      "]):
            links.append({"source": name1, "target": name2, "value": len(fa_set & fb_set)})  
            print([name1,  name2,  len(fa_set & fb_set)], ",")
        # print()
    # Fb --> F
    print()
    print("/* from Fb (marked as 'xxx ') to F (marked as ' xxx') */")
    print([" ",  "  ",  len(trip_ids_list_fb[0] & trip_ids_list_f[0])], ",")  
    for fb_set, name2 in zip(trip_ids_list_fb[1:], ["   ", "    ", "     ", "      "]):
        for f_set, name3 in zip(trip_ids_list_f[1:], ["       ", "        ", "         ", "          "]):
            links.append({"source": name2, "target": name3, "value": len(fb_set & f_set)}) 
            print([name2,  name3,  len(fb_set & f_set)], ",")

# nanjing 
# t = 3
trip_ids_list_fa_nanjing_t3 = [inaccessible_tripIds_nanjing_t3, unreached_trip_ids_nanjing_t3_fa, unacceptable_tripIds_nanjing_t3_fa, pt_trip_ids_nanjing_t3_fa, car_trip_ids_nanjing_t3_fa]
trip_ids_list_fb_nanjing_t3 = [inaccessible_tripIds_nanjing_t3, unreached_trip_ids_nanjing_t3_fb, unacceptable_tripIds_nanjing_t3_fb, pt_trip_ids_nanjing_t3_fb, car_trip_ids_nanjing_t3_fb]
trip_ids_list_f_nanjing_t3 = [inaccessible_tripIds_nanjing_t3, unreached_trip_ids_nanjing_t3_f, unacceptable_tripIds_nanjing_t3_f, pt_trip_ids_nanjing_t3_f, car_trip_ids_nanjing_t3_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_nanjing_t3, trip_ids_list_fb=trip_ids_list_fb_nanjing_t3, trip_ids_list_f=trip_ids_list_f_nanjing_t3, out_file_path=output_file_path)

# t = 4
trip_ids_list_fa_nanjing_t4 = [inaccessible_tripIds_nanjing_t4, unreached_trip_ids_nanjing_t4_fa, unacceptable_tripIds_nanjing_t4_fa, pt_trip_ids_nanjing_t4_fa, car_trip_ids_nanjing_t4_fa]
trip_ids_list_fb_nanjing_t4 = [inaccessible_tripIds_nanjing_t4, unreached_trip_ids_nanjing_t4_fb, unacceptable_tripIds_nanjing_t4_fb, pt_trip_ids_nanjing_t4_fb, car_trip_ids_nanjing_t4_fb]
trip_ids_list_f_nanjing_t4 = [inaccessible_tripIds_nanjing_t4, unreached_trip_ids_nanjing_t4_f, unacceptable_tripIds_nanjing_t4_f, pt_trip_ids_nanjing_t4_f, car_trip_ids_nanjing_t4_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_nanjing_t4, trip_ids_list_fb=trip_ids_list_fb_nanjing_t4, trip_ids_list_f=trip_ids_list_f_nanjing_t4, out_file_path=output_file_path)

# t = 5
trip_ids_list_fa_nanjing_t5 = [inaccessible_tripIds_nanjing_t5, unreached_trip_ids_nanjing_t5_fa, unacceptable_tripIds_nanjing_t5_fa, pt_trip_ids_nanjing_t5_fa, car_trip_ids_nanjing_t5_fa]
trip_ids_list_fb_nanjing_t5 = [inaccessible_tripIds_nanjing_t5, unreached_trip_ids_nanjing_t5_fb, unacceptable_tripIds_nanjing_t5_fb, pt_trip_ids_nanjing_t5_fb, car_trip_ids_nanjing_t5_fb]
trip_ids_list_f_nanjing_t5 = [inaccessible_tripIds_nanjing_t5, unreached_trip_ids_nanjing_t5_f, unacceptable_tripIds_nanjing_t5_f, pt_trip_ids_nanjing_t5_f, car_trip_ids_nanjing_t5_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_nanjing_t5, trip_ids_list_fb=trip_ids_list_fb_nanjing_t5, trip_ids_list_f=trip_ids_list_f_nanjing_t5, out_file_path=output_file_path)

# hamburg 
# t = 4
trip_ids_list_fa_hamburg_t4 = [inaccessible_tripIds_hamburg_t4, unreached_trip_ids_hamburg_t4_fa, unacceptable_tripIds_hamburg_t4_fa, pt_trip_ids_hamburg_t4_fa, car_trip_ids_hamburg_t4_fa]
trip_ids_list_fb_hamburg_t4 = [inaccessible_tripIds_hamburg_t4, unreached_trip_ids_hamburg_t4_fb, unacceptable_tripIds_hamburg_t4_fb, pt_trip_ids_hamburg_t4_fb, car_trip_ids_hamburg_t4_fb]
trip_ids_list_f_hamburg_t4 = [inaccessible_tripIds_hamburg_t4, unreached_trip_ids_hamburg_t4_f, unacceptable_tripIds_hamburg_t4_f, pt_trip_ids_hamburg_t4_f, car_trip_ids_hamburg_t4_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_hamburg_t4, trip_ids_list_fb=trip_ids_list_fb_hamburg_t4, trip_ids_list_f=trip_ids_list_f_hamburg_t4, out_file_path=output_file_path)


# t = 5
trip_ids_list_fa_hamburg_t5 = [inaccessible_tripIds_hamburg_t5, unreached_trip_ids_hamburg_t5_fa, unacceptable_tripIds_hamburg_t5_fa, pt_trip_ids_hamburg_t5_fa, car_trip_ids_hamburg_t5_fa]
trip_ids_list_fb_hamburg_t5 = [inaccessible_tripIds_hamburg_t5, unreached_trip_ids_hamburg_t5_fb, unacceptable_tripIds_hamburg_t5_fb, pt_trip_ids_hamburg_t5_fb, car_trip_ids_hamburg_t5_fb]
trip_ids_list_f_hamburg_t5 = [inaccessible_tripIds_hamburg_t5, unreached_trip_ids_hamburg_t5_f, unacceptable_tripIds_hamburg_t5_f, pt_trip_ids_hamburg_t5_f, car_trip_ids_hamburg_t5_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_hamburg_t5, trip_ids_list_fb=trip_ids_list_fb_hamburg_t5, trip_ids_list_f=trip_ids_list_f_hamburg_t5, out_file_path=output_file_path)

# t = 6
trip_ids_list_fa_hamburg_t6 = [inaccessible_tripIds_hamburg_t6, unreached_trip_ids_hamburg_t6_fa, unacceptable_tripIds_hamburg_t6_fa, pt_trip_ids_hamburg_t6_fa, car_trip_ids_hamburg_t6_fa]
trip_ids_list_fb_hamburg_t6 = [inaccessible_tripIds_hamburg_t6, unreached_trip_ids_hamburg_t6_fb, unacceptable_tripIds_hamburg_t6_fb, pt_trip_ids_hamburg_t6_fb, car_trip_ids_hamburg_t6_fb]
trip_ids_list_f_hamburg_t6 = [inaccessible_tripIds_hamburg_t6, unreached_trip_ids_hamburg_t6_f, unacceptable_tripIds_hamburg_t6_f, pt_trip_ids_hamburg_t6_f, car_trip_ids_hamburg_t6_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_hamburg_t6, trip_ids_list_fb=trip_ids_list_fb_hamburg_t6, trip_ids_list_f=trip_ids_list_f_hamburg_t6, out_file_path=output_file_path)


# la 
# t = 0.07
trip_ids_list_fa_la_t007 = [inaccessible_tripIds_la_t007, unreached_trip_ids_la_t007_fa, unacceptable_tripIds_la_t007_fa, pt_trip_ids_la_t007_fa, car_trip_ids_la_t007_fa]
trip_ids_list_fb_la_t007 = [inaccessible_tripIds_la_t007, unreached_trip_ids_la_t007_fb, unacceptable_tripIds_la_t007_fb, pt_trip_ids_la_t007_fb, car_trip_ids_la_t007_fb]
trip_ids_list_f_la_t007 = [inaccessible_tripIds_la_t007, unreached_trip_ids_la_t007_f, unacceptable_tripIds_la_t007_f, pt_trip_ids_la_t007_f, car_trip_ids_la_t007_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_la_t007, trip_ids_list_fb=trip_ids_list_fb_la_t007, trip_ids_list_f=trip_ids_list_f_la_t007, out_file_path=output_file_path)

# t = 0.3
trip_ids_list_fa_la_t03 = [inaccessible_tripIds_la_t03, unreached_trip_ids_la_t03_fa, unacceptable_tripIds_la_t03_fa, pt_trip_ids_la_t03_fa, car_trip_ids_la_t03_fa]
trip_ids_list_fb_la_t03 = [inaccessible_tripIds_la_t03, unreached_trip_ids_la_t03_fb, unacceptable_tripIds_la_t03_fb, pt_trip_ids_la_t03_fb, car_trip_ids_la_t03_fb]
trip_ids_list_f_la_t03 = [inaccessible_tripIds_la_t03, unreached_trip_ids_la_t03_f, unacceptable_tripIds_la_t03_f, pt_trip_ids_la_t03_f, car_trip_ids_la_t03_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_la_t03, trip_ids_list_fb=trip_ids_list_fb_la_t03, trip_ids_list_f=trip_ids_list_f_la_t03, out_file_path=output_file_path)

# t = 0.07
trip_ids_list_fa_la_t06 = [inaccessible_tripIds_la_t06, unreached_trip_ids_la_t06_fa, unacceptable_tripIds_la_t06_fa, pt_trip_ids_la_t06_fa, car_trip_ids_la_t06_fa]
trip_ids_list_fb_la_t06 = [inaccessible_tripIds_la_t06, unreached_trip_ids_la_t06_fb, unacceptable_tripIds_la_t06_fb, pt_trip_ids_la_t06_fb, car_trip_ids_la_t06_fb]
trip_ids_list_f_la_t06 = [inaccessible_tripIds_la_t06, unreached_trip_ids_la_t06_f, unacceptable_tripIds_la_t06_f, pt_trip_ids_la_t06_f, car_trip_ids_la_t06_f]
output_file_path = r""
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_la_t06, trip_ids_list_fb=trip_ids_list_fb_la_t06, trip_ids_list_f=trip_ids_list_f_la_t06, out_file_path=output_file_path)


""" 6. # Using google chart templates for Sankey diagrams  https://developers.google.com/chart/interactive/docs/gallery/sankey """
